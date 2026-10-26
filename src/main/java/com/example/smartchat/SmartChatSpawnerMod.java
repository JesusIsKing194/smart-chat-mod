package com.example.smartchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartChatSpawnerMod implements ModInitializer {

    private static final Set<String> BLOCK_BLACKLIST = Set.of(
            "bedrock", "command_block", "chain_command_block", "repeating_command_block",
            "barrier", "structure_block", "end_portal_frame", "structure_void"
    );

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "op me", "admin", "creative mode", "give me op"
    );

    private static final Set<String> INTENT_KEYWORDS = Set.of(
            "give", "spawn", "drop", "can", "i", "want", "get", "need", "teleport", "tp", "rtp", 
            "xray", "summon", "fly", "speed", "time", "weather", "heal", "god", "rain", "day", "night"
    );

    // Pre-computed lookup cache to optimize performance and prevent garbage collection stutters
    private static final Map<String, Item> ITEM_CACHE = new HashMap<>();
    private static final Map<String, EntityType<?>> ENTITY_CACHE = new HashMap<>();

    @Override
    public void onInitialize() {
        // Initialize caches on server start
        for (Identifier id : Registries.ITEM.getIds()) {
            ITEM_CACHE.put(id.getPath().toLowerCase(), Registries.ITEM.get(id));
        }
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            ENTITY_CACHE.put(id.getPath().toLowerCase(), Registries.ENTITY_TYPE.get(id));
        }

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString().toLowerCase();
            if (text.length() > 150) return; // ReDoS length safeguard
            sender.getServer().execute(() -> parseAndExecuteRequest(sender, text));
        });
    }

    private void parseAndExecuteRequest(ServerPlayerEntity player, String input) {
        // 1. Forbidden Word Troll Trap (Explosive TNT punishment for trying to hack admin)
        for (String forbidden : FORBIDDEN_WORDS) {
            if (input.contains(forbidden)) {
                triggerAdminTroll(player);
                return;
            }
        }

        boolean hasIntent = false;
        for (String keyword : INTENT_KEYWORDS) {
            if (input.contains(keyword)) {
                hasIntent = true;
                break;
            }
        }
        if (!hasIntent) return;

        // 2. Environment Controls (Weather & Time)
        if (input.contains("weather") || input.contains("rain") || input.contains("sun") || input.contains("clear")) {
            executeWeatherCommand(player, input);
            return;
        }

        if (input.contains("time") || input.contains("day") || input.contains("night")) {
            executeTimeCommand(player, input);
            return;
        }

        // 3. Teleport Controls (RTP & Player TP)
        if (input.contains("rtp") || input.contains("random teleport") || input.contains("random tp")) {
            executeRandomTeleport(player);
            return;
        }

        if (input.contains("teleport me to") || input.contains("tp me to") || input.contains("tp to")) {
            executePlayerTeleport(player, input);
            return;
        }

        // 4. X-Ray & Vision Controls
        if (input.contains("xray") || input.contains("x-ray")) {
            executeXrayCommand(player, input);
            return;
        }

        // 5. Flight, Speed, & God Mode
        if (input.contains("fly") || input.contains("flight")) {
            toggleFlight(player);
            return;
        }

        if (input.contains("speed")) {
            applySpeedBoost(player);
            return;
        }

        if (input.contains("heal") || input.contains("god")) {
            executeHealCommand(player);
            return;
        }

        // 6. Mob Summoning System
        if (input.contains("summon") || input.contains("spawn") && isMobRequest(input)) {
            executeMobSummon(player, input);
            return;
        }

        // 7. Politeness Check for Item Spawns
        boolean isPolite = input.contains("please") || input.contains("plz") || input.contains("kindly");
        if (!isPolite && Math.random() < 0.6) {
            punishRudePlayer(player, input);
            return;
        }

        // 8. Item & Block Spawning Pipeline
        String processedInput = expandSlang(input);

        int count = 1;
        Matcher numMatcher = Pattern.compile("\\b(\\d+)\\b").matcher(processedInput);
        if (numMatcher.find()) {
            try {
                count = Math.min(64, Math.max(1, Integer.parseInt(numMatcher.group(1))));
            } catch (NumberFormatException ignored) {}
        }

        Item matchedItem = findBestRegistryMatch(processedInput);
        if (matchedItem == null || matchedItem == Items.AIR) {
            spawnGagItem(player, Items.ROTTEN_FLESH);
            return;
        }

        Identifier itemId = Registries.ITEM.getId(matchedItem);
        String itemPath = itemId.getPath();

        // Normalize and check blacklist (stripping plural suffixes)
        String normalizedPath = itemPath.replaceAll("s$|es$", "");
        if (BLOCK_BLACKLIST.contains(itemPath) || BLOCK_BLACKLIST.contains(normalizedPath)) {
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        BlockPos playerPos = player.getBlockPos();
        player.getWorld().playSound(null, playerPos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
        spawnThematicParticles(player, itemPath);

        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(playerPos)) return;

        if (matchedItem instanceof BlockItem blockItem && isInteractableBlock(blockItem.getBlock())) {
            Block block = blockItem.getBlock();
            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = playerPos.up(8 + (i * 2));
                FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, spawnPos, block.getDefaultState());
                if (fallingBlock != null) {
                    fallingBlock.timeFalling = 1;
                    fallingBlock.dropItem = false;
                }
            }
        } else {
            ItemStack stack = new ItemStack(matchedItem, 1);
            applyEnchantmentsToStack(player, stack, processedInput);
            applyCustomNaming(stack, input);

            Random rand = new Random();
            for (int i = 0; i < count; i++) {
                double offsetX = (rand.nextDouble() - 0.5) * 1.5;
                double offsetZ = (rand.nextDouble() - 0.5) * 1.5;

                ItemEntity itemEntity = new ItemEntity(
                        world,
                        playerPos.getX() + 0.5 + offsetX,
                        playerPos.getY() + 3.0,
                        playerPos.getZ() + 0.5 + offsetZ,
                        stack.copy()
                );
                world.spawnEntity(itemEntity);
            }
        }
    }

    // --- Admin Troll Trap ---
    private void triggerAdminTroll(ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        player.getWorld().playSound(null, playerPos, SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.PLAYERS, 1.0f, 0.5f);
        
        TntEntity tnt = new TntEntity(player.getWorld(), playerPos.getX() + 0.5, playerPos.getY() + 6.0, playerPos.getZ() + 0.5, player);
        tnt.setFuse(40);
        player.getWorld().spawnEntity(tnt);
    }

    // --- Mob Summoning ---
    private boolean isMobRequest(String input) {
        for (String mobName : ENTITY_CACHE.keySet()) {
            if (input.contains(mobName)) return true;
        }
        return false;
    }

    private void executeMobSummon(ServerPlayerEntity player, String input) {
        int count = 1;
        Matcher numMatcher = Pattern.compile("\\b(\\d+)\\b").matcher(input);
        if (numMatcher.find()) {
            try {
                count = Math.min(10, Math.max(1, Integer.parseInt(numMatcher.group(1))));
            } catch (NumberFormatException ignored) {}
        }

        EntityType<?> targetType = null;
        int longestMatch = -1;
        for (Map.Entry<String, EntityType<?>> entry : ENTITY_CACHE.entrySet()) {
            if (input.contains(entry.getKey()) && entry.getKey().length() > longestMatch) {
                longestMatch = entry.getKey().length();
                targetType = entry.getValue();
            }
        }

        if (targetType != null) {
            BlockPos spawnPos = player.getBlockPos().up(4);
            ServerWorld world = player.getServerWorld();
            if (!world.isChunkLoaded(spawnPos)) return;

            for (int i = 0; i < count; i++) {
                Entity entity = targetType.create(world);
                if (entity != null) {
                    entity.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    world.spawnEntity(entity);
                }
            }
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    // --- Custom Naming & Lore ---
    private void applyCustomNaming(ItemStack stack, String input) {
        if (input.contains("named") || input.contains("called")) {
            String[] split = input.split("(named|called)\\s+");
            if (split.length > 1) {
                String rawName = split[1].trim().split("\\b(please|plz|and|with)\\b")[0].trim();
                if (!rawName.isEmpty()) {
                    String customName = rawName.substring(0, 1).toUpperCase() + rawName.substring(1);
                    stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + customName));
                }
            }
        }
    }

    // --- Flight, Speed, & Healing ---
    private void toggleFlight(ServerPlayerEntity player) {
        boolean allowFlight = !player.getAbilities().allowFlying;
        player.getAbilities().allowFlying = allowFlight;
        player.sendAbilitiesUpdate();
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void applySpeedBoost(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 2, false, false));
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
    }

    private void executeHealCommand(ServerPlayerEntity player) {
        player.heal(20.0f);
        player.getHungerManager().setFoodLevel(20);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, false, false));
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 2.0f);
    }

    // --- Weather & Time ---
    private void executeWeatherCommand(ServerPlayerEntity player, String input) {
        ServerWorld world = player.getServerWorld();
        if (input.contains("rain") || input.contains("storm")) {
            world.setWeather(0, 6000, true, false);
        } else {
            world.setWeather(6000, 0, false, false);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void executeTimeCommand(ServerPlayerEntity player, String input) {
        ServerWorld world = player.getServerWorld();
        if (input.contains("night")) {
            world.setTimeOfDay(18000);
        } else {
            world.setTimeOfDay(1000);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    // --- Material Glow Particles ---
    private void spawnThematicParticles(ServerPlayerEntity player, String itemPath) {
        BlockPos pos = player.getBlockPos().up(3);
        ServerWorld world = player.getServerWorld();

        if (itemPath.contains("diamond")) {
            world.spawnParticles(ParticleTypes.ENCHANT, pos.getX(), pos.getY(), pos.getZ(), 25, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("gold")) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("emerald")) {
            world.spawnParticles(ParticleTypes.COMPOSTER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("netherite")) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.05);
        } else {
            world.spawnParticles(ParticleTypes.PORTAL, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.2);
        }
    }

    // --- Safe RTP with Liquid Re-rolls ---
    private void executeRandomTeleport(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random rand = new Random();
        int radius = 500;
        int x = 0, y = 0, z = 0;
        boolean safeLanding = false;

        for (int attempt = 0; attempt < 10; attempt++) {
            x = player.getBlockX() + rand.nextInt(radius * 2) - radius;
            z = player.getBlockZ() + rand.nextInt(radius * 2) - radius;
            y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos landingPos = new BlockPos(x, y - 1, z);
            Block block = world.getBlockState(landingPos).getBlock();

            if (block != Blocks.WATER && block != Blocks.LAVA && block != Blocks.AIR) {
                safeLanding = true;
                break;
            }
        }

        player.teleport(world, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void executePlayerTeleport(ServerPlayerEntity player, String input) {
        String[] parts = input.split("to\\s+");
        if (parts.length < 2) return;

        String query = parts[1].trim().toLowerCase();
        ServerPlayerEntity bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (ServerPlayerEntity onlinePlayer : player.getServer().getPlayerManager().getPlayerList()) {
            String targetName = onlinePlayer.getName().getString().toLowerCase();
            if (targetName.startsWith(query) || targetName.contains(query)) {
                bestMatch = onlinePlayer;
                break;
            }
            int dist = getLevenshteinDistance(query, targetName);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestMatch = onlinePlayer;
            }
        }

        if (bestMatch != null && bestMatch != player) {
            player.teleport(bestMatch.getServerWorld(), bestMatch.getX(), bestMatch.getY(), bestMatch.getZ(), player.getYaw(), player.getPitch());
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void executeXrayCommand(ServerPlayerEntity player, String input) {
        if (input.contains("off") || input.contains("stop") || input.contains("disable")) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            player.removeStatusEffect(StatusEffects.GLOWING);
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }

        int durationTicks = 12000;
        Matcher numMatcher = Pattern.compile("\\b(\\d+)\\b").matcher(input);
        if (numMatcher.find()) {
            try {
                durationTicks = Integer.parseInt(numMatcher.group(1)) * 20;
            } catch (NumberFormatException ignored) {}
        }

        player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        player.removeStatusEffect(StatusEffects.GLOWING);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, durationTicks, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, durationTicks, 0, false, false));
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    private void punishRudePlayer(ServerPlayerEntity player, String input) {
        BlockPos playerPos = player.getBlockPos();
        player.getWorld().playSound(null, playerPos, SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.PLAYERS, 1.0f, 0.8f);

        if (input.contains("block")) {
            FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(player.getWorld(), playerPos.up(8), Blocks.ANVIL.getDefaultState());
            if (anvil != null) {
                anvil.timeFalling = 1;
                anvil.dropItem = true;
            }
        } else {
            ItemStack potato = new ItemStack(Items.POISONOUS_POTATO, 3);
            ItemEntity entity = new ItemEntity(player.getWorld(), playerPos.getX() + 0.5, playerPos.getY() + 3.0, playerPos.getZ() + 0.5, potato);
            player.getWorld().spawnEntity(entity);
        }
    }

    private void spawnGagItem(ServerPlayerEntity player, Item gagItem) {
        BlockPos playerPos = player.getBlockPos();
        player.getWorld().playSound(null, playerPos, SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        ItemStack stack = new ItemStack(gagItem, 1);
        ItemEntity entity = new ItemEntity(player.getWorld(), playerPos.getX() + 0.5, playerPos.getY() + 3.0, playerPos.getZ() + 0.5, stack);
        player.getWorld().spawnEntity(entity);
    }

    private String expandSlang(String input) {
        return input.replaceAll("\\bdia\\b", "diamond")
                    .replaceAll("\\bneth\\b", "netherite")
                    .replaceAll("\\beff\\b", "efficiency")
                    .replaceAll("\\bsharp\\b", "sharpness")
                    .replaceAll("\\bpick\\b", "pickaxe")
                    .replaceAll("\\bswrd\\b", "sword");
    }

    private Item findBestRegistryMatch(String input) {
        String cleanInput = input.replace(" ", "_");
        Item bestMatch = null;
        int longestPathLength = -1;

        for (Map.Entry<String, Item> entry : ITEM_CACHE.entrySet()) {
            String path = entry.getKey();
            String pathReadable = path.replace("_", " ");

            if (cleanInput.contains(path) || input.contains(pathReadable)) {
                if (path.length() > longestPathLength) {
                    longestPathLength = path.length();
                    bestMatch = entry.getValue();
                }
            }
        }

        if (bestMatch == null) {
            for (Map.Entry<String, Item> entry : ITEM_CACHE.entrySet()) {
                String path = entry.getKey();
                for (String word : input.split("\\s+")) {
                    if (word.length() < 4) continue;
                    int maxAllowedDistance = Math.max(1, word.length() / 4);
                    if (getLevenshteinDistance(word, path.replace("_", "")) <= maxAllowedDistance) {
                        if (path.length() > longestPathLength) {
                            longestPathLength = path.length();
                            bestMatch = entry.getValue();
                        }
                    }
                }
            }
        }
        return bestMatch;
    }

    private boolean isInteractableBlock(Block block) {
        if (block instanceof BlockWithEntity) return true;
        return block instanceof AnvilBlock || block instanceof CraftingTableBlock || block instanceof EnderChestBlock ||
               block instanceof EnchantingTableBlock || block instanceof LoomBlock || block instanceof CartographyTableBlock ||
               block instanceof GrindstoneBlock || block instanceof StonecutterBlock || block instanceof SmithingTableBlock;
    }

    private void applyEnchantmentsToStack(ServerPlayerEntity player, ItemStack stack, String input) {
        var registry = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        if (input.contains("efficiency") || input.contains("effcency") || input.contains("fast mining")) {
            addEnc(registry, stack, "efficiency", 5);
        }
        if (input.contains("sharpness") || input.contains("shprness") || input.contains("damage")) {
            addEnc(registry, stack, "sharpness", 5);
        }
        if (input.contains("fortune")) {
            addEnc(registry, stack, "fortune", 3);
        }
        if (input.contains("unbreaking") || input.contains("durable")) {
            addEnc(registry, stack, "unbreaking", 3);
        }
        if (input.contains("fire aspect") || input.contains("flame")) {
            addEnc(registry, stack, "fire_aspect", 2);
        }
    }

    private void addEnc(net.minecraft.registry.Registry<Enchantment> registry, ItemStack stack, String name, int level) {
        registry.getEntry(Identifier.of("minecraft", name)).ifPresent(entry -> {
            stack.addEnchantment(entry, level);
        });
    }

    private int getLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
