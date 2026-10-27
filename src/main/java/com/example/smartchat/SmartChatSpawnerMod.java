package com.example.smartchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        // Initialize caches
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            ITEM_CACHE.put(id.getPath().toLowerCase(), BuiltInRegistries.ITEM.getValue(id));
        }
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            ENTITY_CACHE.put(id.getPath().toLowerCase(), BuiltInRegistries.ENTITY_TYPE.getValue(id));
        }

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.decoratedContent().getString().toLowerCase();
            if (text.length() > 150) return; // ReDoS length safeguard
            MinecraftServer server = sender.level().getServer();
            if (server == null) return;
            server.execute(() -> parseAndExecuteRequest(sender, text));
        });
    }

    // Helper: get the ServerLevel of a player
    private ServerLevel levelOf(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    // Helper: run a command as the server (used for time, weather, and teleports)
    private void runCommand(ServerLevel world, String command) {
        MinecraftServer server = world.getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private void parseAndExecuteRequest(ServerPlayer player, String input) {
        // 1. Forbidden Word Troll Trap
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

        Identifier itemId = BuiltInRegistries.ITEM.getKey(matchedItem);
        String itemPath = itemId.getPath();

        // Normalize and check blacklist (stripping plural suffixes)
        String normalizedPath = itemPath.replaceAll("s$|es$", "");
        ServerLevel world = levelOf(player);
        if (BLOCK_BLACKLIST.contains(itemPath) || BLOCK_BLACKLIST.contains(normalizedPath)) {
            world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }

        BlockPos playerPos = player.blockPosition();
        world.playSound(null, playerPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
        spawnThematicParticles(player, itemPath);

        if (!world.hasChunkAt(playerPos)) return;

        if (matchedItem instanceof BlockItem blockItem && isInteractableBlock(blockItem.getBlock())) {
            Block block = blockItem.getBlock();
            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = playerPos.above(8 + (i * 2));
                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(world, spawnPos, block.defaultBlockState());
                if (fallingBlock != null) {
                    fallingBlock.time = 1;
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
                world.addFreshEntity(itemEntity);
            }
        }
    }

    // --- Admin Troll Trap ---
    private void triggerAdminTroll(ServerPlayer player) {
        ServerLevel world = levelOf(player);
        BlockPos playerPos = player.blockPosition();
        world.playSound(null, playerPos, SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 1.0f, 0.5f);

        PrimedTnt tnt = new PrimedTnt(world, playerPos.getX() + 0.5, playerPos.getY() + 6.0, playerPos.getZ() + 0.5, player);
        tnt.setFuse(40);
        world.addFreshEntity(tnt);
    }

    // --- Mob Summoning ---
    private boolean isMobRequest(String input) {
        for (String mobName : ENTITY_CACHE.keySet()) {
            if (input.contains(mobName)) return true;
        }
        return false;
    }

    private void executeMobSummon(ServerPlayer player, String input) {
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
            BlockPos spawnPos = player.blockPosition().above(4);
            ServerLevel world = levelOf(player);
            if (!world.hasChunkAt(spawnPos)) return;

            for (int i = 0; i < count; i++) {
                Entity entity = targetType.create(world, EntitySpawnReason.COMMAND);
                if (entity != null) {
                    entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    world.addFreshEntity(entity);
                }
            }
            world.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    // --- Custom Naming ---
    private void applyCustomNaming(ItemStack stack, String input) {
        if (input.contains("named") || input.contains("called")) {
            String[] split = input.split("(named|called)\\s+");
            if (split.length > 1) {
                String rawName = split[1].trim().split("\\b(please|plz|and|with)\\b")[0].trim();
                if (!rawName.isEmpty()) {
                    String customName = rawName.substring(0, 1).toUpperCase() + rawName.substring(1);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(customName).withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }

    // --- Flight, Speed, & Healing ---
    private void toggleFlight(ServerPlayer player) {
        boolean allowFlight = !player.getAbilities().mayfly;
        player.getAbilities().mayfly = allowFlight;
        player.onUpdateAbilities();
        levelOf(player).playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    private void applySpeedBoost(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 2, false, false));
        levelOf(player).playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    private void executeHealCommand(ServerPlayer player) {
        player.heal(20.0f);
        player.getFoodData().setFoodLevel(20);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
        levelOf(player).playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 2.0f);
    }

    // --- Weather & Time (done through vanilla commands so they work across versions) ---
    private void executeWeatherCommand(ServerPlayer player, String input) {
        ServerLevel world = levelOf(player);
        if (input.contains("rain") || input.contains("storm")) {
            runCommand(world, "weather rain");
        } else {
            runCommand(world, "weather clear");
        }
        world.playSound(null, player.blockPosition(), SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private void executeTimeCommand(ServerPlayer player, String input) {
        ServerLevel world = levelOf(player);
        if (input.contains("night")) {
            runCommand(world, "time set night");
        } else {
            runCommand(world, "time set day");
        }
        world.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    // --- Material Glow Particles ---
    private void spawnThematicParticles(ServerPlayer player, String itemPath) {
        BlockPos pos = player.blockPosition().above(3);
        ServerLevel world = levelOf(player);

        if (itemPath.contains("diamond")) {
            world.sendParticles(ParticleTypes.ENCHANT, pos.getX(), pos.getY(), pos.getZ(), 25, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("gold")) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("emerald")) {
            world.sendParticles(ParticleTypes.COMPOSTER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (itemPath.contains("netherite")) {
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.05);
        } else {
            world.sendParticles(ParticleTypes.PORTAL, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.2);
        }
    }

    // --- Safe RTP with Liquid Re-rolls ---
    private void executeRandomTeleport(ServerPlayer player) {
        ServerLevel world = levelOf(player);
        Random rand = new Random();
        int radius = 500;
        int x = 0, y = 0, z = 0;

        for (int attempt = 0; attempt < 10; attempt++) {
            x = player.getBlockX() + rand.nextInt(radius * 2) - radius;
            z = player.getBlockZ() + rand.nextInt(radius * 2) - radius;
            y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos landingPos = new BlockPos(x, y - 1, z);
            Block block = world.getBlockState(landingPos).getBlock();

            if (block != Blocks.WATER && block != Blocks.LAVA && block != Blocks.AIR) {
                break;
            }
        }

        String uuid = player.getUUID().toString();
        // "execute as ... at @s" keeps the teleport inside the player's own dimension
        runCommand(world, "execute as " + uuid + " at @s run tp @s " + (x + 0.5) + " " + y + " " + (z + 0.5));
        world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private void executePlayerTeleport(ServerPlayer player, String input) {
        String[] parts = input.split("to\\s+");
        if (parts.length < 2) return;

        String query = parts[1].trim().toLowerCase();
        ServerPlayer bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        ServerLevel world = levelOf(player);

        for (ServerPlayer onlinePlayer : world.getServer().getPlayerList().getPlayers()) {
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
            // Teleporting to an entity works across dimensions
            runCommand(world, "tp " + player.getUUID() + " " + bestMatch.getUUID());
            world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void executeXrayCommand(ServerPlayer player, String input) {
        ServerLevel world = levelOf(player);
        if (input.contains("off") || input.contains("stop") || input.contains("disable")) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.removeEffect(MobEffects.GLOWING);
            world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.5f);
            return;
        }

        int durationTicks = 12000;
        Matcher numMatcher = Pattern.compile("\\b(\\d+)\\b").matcher(input);
        if (numMatcher.find()) {
            try {
                durationTicks = Integer.parseInt(numMatcher.group(1)) * 20;
            } catch (NumberFormatException ignored) {}
        }

        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.GLOWING);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, false, false));
        world.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private void punishRudePlayer(ServerPlayer player, String input) {
        ServerLevel world = levelOf(player);
        BlockPos playerPos = player.blockPosition();
        world.playSound(null, playerPos, SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 1.0f, 0.8f);

        if (input.contains("block")) {
            FallingBlockEntity anvil = FallingBlockEntity.fall(world, playerPos.above(8), Blocks.ANVIL.defaultBlockState());
            if (anvil != null) {
                anvil.time = 1;
                anvil.dropItem = true;
            }
        } else {
            ItemStack potato = new ItemStack(Items.POISONOUS_POTATO, 3);
            ItemEntity entity = new ItemEntity(world, playerPos.getX() + 0.5, playerPos.getY() + 3.0, playerPos.getZ() + 0.5, potato);
            world.addFreshEntity(entity);
        }
    }

    private void spawnGagItem(ServerPlayer player, Item gagItem) {
        ServerLevel world = levelOf(player);
        BlockPos playerPos = player.blockPosition();
        world.playSound(null, playerPos, SoundEvents.VILLAGER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
        ItemStack stack = new ItemStack(gagItem, 1);
        ItemEntity entity = new ItemEntity(world, playerPos.getX() + 0.5, playerPos.getY() + 3.0, playerPos.getZ() + 0.5, stack);
        world.addFreshEntity(entity);
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
        if (block instanceof BaseEntityBlock) return true;
        return block instanceof AnvilBlock || block instanceof CraftingTableBlock || block instanceof EnderChestBlock ||
               block instanceof EnchantingTableBlock || block instanceof LoomBlock || block instanceof CartographyTableBlock ||
               block instanceof GrindstoneBlock || block instanceof StonecutterBlock || block instanceof SmithingTableBlock;
    }

    private void applyEnchantmentsToStack(ServerPlayer player, ItemStack stack, String input) {
        HolderLookup.RegistryLookup<Enchantment> registry =
                levelOf(player).registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

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

    private void addEnc(HolderLookup.RegistryLookup<Enchantment> registry, ItemStack stack, String name, int level) {
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(name));
        registry.get(key).ifPresent(holder -> stack.enchant(holder, level));
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
