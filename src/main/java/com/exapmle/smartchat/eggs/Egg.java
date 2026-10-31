package com.example.smartchat.eggs;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.engine.ItemRequest;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The easter egg engine. Egg groups (WorldEggs, PlayerEggs, ...) register their eggs with Egg.define(...);
 * the Egg.Listener chat handler triggers them, tracks which ones each player has discovered, and enforces cooldowns.
 * Each egg has its own on/off switch in the settings file (numbered, so the config does not spoil the secrets).
 */
public final class Egg {

    static {
        Settings.describeSection("eggs", "Easter eggs");
    }

    public static final Setting ENABLED = Settings.bool("eggs", "enabled", true,
            "Master switch for all easter eggs.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("eggs", "cooldown_seconds", 20, 0, 600,
            "Seconds before the same player can trigger the same egg again.");

    public static final Setting GLOBAL_COOLDOWN_SECONDS = Settings.integer("eggs", "global_cooldown_seconds", 3, 0, 60,
            "Seconds a player must wait between any two eggs.");

    public static final Setting MAX_WORDS = Settings.integer("eggs", "max_message_words", 8, 1, 40,
            "Eggs only trigger in short messages (this many words or fewer), so long sentences don't set them off by accident.");

    public static final Setting ANNOUNCE = Settings.bool("eggs", "announce_discovery", true,
            "Tell a player when they discover an egg for the first time.");

    public enum Category {
        CREATURES("Creatures"), WORLD("World"), PLAYER("Player"), GIFTS("Food and gifts"), ASSISTANT("Assistant and games");

        public final String title;

        Category(String title) {
            this.title = title;
        }
    }

    @FunctionalInterface
    public interface Action {
        void run(ChatContext ctx) throws Exception;
    }

    public final int number;
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final List<String> triggers;
    /** Null for observe-only eggs: another handler does the work and the egg is just marked as discovered. */
    public final Action action;
    public final boolean observeOnly;
    /** When true the whole message (ignoring polite words) must equal a trigger, not just contain it. */
    public final boolean exactOnly;
    public final Setting enabled;

    private static final List<Egg> ALL = new ArrayList<>();
    private static final Map<String, Egg> BY_ID = new HashMap<>();

    private Egg(int number, String id, String name, String description, Category category, List<String> triggers,
                Action action, boolean observeOnly, boolean exactOnly, Setting enabled) {
        this.exactOnly = exactOnly;
        this.number = number;
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.triggers = triggers;
        this.action = action;
        this.observeOnly = observeOnly;
        this.enabled = enabled;
    }

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ registry

    public static Builder define(String id, String name, Category category, String description, String... triggers) {
        return new Builder(id, name, category, description, triggers);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final Category category;
        private final String description;
        private final List<String> triggers = new ArrayList<>();
        private boolean exact = false;

        private Builder(String id, String name, Category category, String description, String[] triggers) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.triggers.addAll(Arrays.asList(triggers));
        }

        /** More phrases that trigger the same egg. */
        public Builder also(String... more) {
            triggers.addAll(Arrays.asList(more));
            return this;
        }

        /** The message must be just the trigger (polite words allowed), so ordinary sentences never set it off. */
        public Builder exact() {
            exact = true;
            return this;
        }

        /** Registers an egg that does something when triggered. The chat message is used up. */
        public void run(Action action) {
            register(action, false);
        }

        /** Registers an egg handled by another part of the mod: it is only marked as discovered. */
        public void observe() {
            register(null, true);
        }

        private void register(Action action, boolean observeOnly) {
            int number = ALL.size() + 1;
            List<String> normalized = new ArrayList<>();
            for (String t : triggers) {
                String n = TextUtil.normalize(t);
                if (!n.isEmpty()) normalized.add(n);
            }
            Setting setting = Settings.bool("eggs", String.format("egg_%02d", number), true,
                    "Easter egg " + number + " on/off. (Admins can see which one it is in the How to Use menu.)");
            Egg egg = new Egg(number, id, name, description, category, normalized, action, observeOnly, exact, setting);
            ALL.add(egg);
            BY_ID.put(id, egg);
        }
    }

    public static List<Egg> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Egg byId(String id) {
        return BY_ID.get(id);
    }

    public static int total() {
        return ALL.size();
    }

    public static boolean isDiscovered(ServerPlayer player, String id) {
        return PlayerDataStore.hasEgg(player, id);
    }

    public static int discoveredCount(ServerPlayer player) {
        int n = 0;
        for (Egg egg : ALL) {
            if (isDiscovered(player, egg.id)) n++;
        }
        return n;
    }

    /** Marks the egg as discovered for this player and, the first time, tells them. */
    public static void discover(ServerPlayer player, Egg egg) {
        if (!PlayerDataStore.discoverEgg(player, egg.id)) return;
        if (!ANNOUNCE.asBoolean()) return;
        Messages.say(player, "Secret discovered: " + egg.name + "! (" + discoveredCount(player) + "/" + total() + ")");
        ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ chat listener

    /** Register this near the front of the chat router (priority about 40). */
    public static final class Listener implements ChatRouter.Handler {

        private static final Map<UUID, Map<String, Long>> LAST_EGG = new HashMap<>();
        private static final Map<UUID, Long> LAST_ANY = new HashMap<>();

        private static String withoutPoliteWords(String text) {
            String result = text;
            for (String word : Settings.POLITE_WORDS.asList()) result = TextUtil.removePhrase(result, word);
            return result;
        }

        @Override
        public boolean handle(ChatContext ctx) {
            if (!ENABLED.asBoolean() || ALL.isEmpty()) return false;
            if (ctx.tokens.size() > MAX_WORDS.asInt()) return false;

            String stripped = withoutPoliteWords(ctx.text);
            Egg best = null;
            int bestLength = 0;
            for (Egg egg : ALL) {
                for (String trigger : egg.triggers) {
                    if (trigger.length() <= bestLength) continue;
                    boolean hit = egg.exactOnly ? stripped.equals(trigger) : ctx.has(trigger);
                    if (hit) {
                        best = egg;
                        bestLength = trigger.length();
                    }
                }
            }
            if (best == null || !best.enabled.asBoolean()) return false;

            // a real item request ("can i get fireworks") wins over an egg
            if (!best.observeOnly && ctx.intents().primary() == Intent.ITEM) {
                ItemRequest request = ctx.itemRequest();
                if (request != null && request.confidence >= 0.95) return false;
            }

            ServerPlayer player = ctx.player;
            UUID id = player.getUUID();
            long now = System.currentTimeMillis();

            if (best.observeOnly) {
                discover(player, best);
                return false;
            }

            int eggCooldown = COOLDOWN_SECONDS.asInt();
            Map<String, Long> mine = LAST_EGG.computeIfAbsent(id, k -> new HashMap<>());
            Long lastThis = mine.get(best.id);
            if (eggCooldown > 0 && lastThis != null && now - lastThis < eggCooldown * 1000L) {
                Messages.actionBar(player, "That one needs a moment to recharge...");
                return true;
            }
            int globalCooldown = GLOBAL_COOLDOWN_SECONDS.asInt();
            Long lastAny = LAST_ANY.get(id);
            if (globalCooldown > 0 && lastAny != null && now - lastAny < globalCooldown * 1000L) {
                return true;
            }

            mine.put(best.id, now);
            LAST_ANY.put(id, now);
            try {
                best.action.run(ctx);
                discover(player, best);
            } catch (Exception e) {
                com.example.smartchat.SmartChatSpawnerMod.LOGGER.error("Easter egg '{}' failed", best.id, e);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ helpers shared by all egg groups

    /** Small building blocks so the egg files stay short. */
    public static final class Tools {

        private Tools() {}

        public static void command(ServerLevel level, String command) {
            MinecraftServer server = level.getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }

        public static void effect(ServerLevel level, ServerPlayer player, String effect, int seconds, int amplifier) {
            command(level, "effect give " + player.getUUID() + " minecraft:" + effect + " " + seconds + " "
                    + amplifier + " true");
        }

        public static void clearEffect(ServerLevel level, ServerPlayer player, String effect) {
            command(level, "effect clear " + player.getUUID() + " minecraft:" + effect);
        }

        public static void sound(ServerLevel level, BlockPos pos, SoundEvent event, float volume, float pitch) {
            level.playSound(null, pos, event, SoundSource.PLAYERS, volume, pitch);
        }

        public static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                     int count, double spread, double speed) {
            level.sendParticles(type, x, y, z, count, spread, spread, spread, speed);
        }

        /** A stack of the item with this id (like "golden_apple"), or null when the id does not exist. */
        public static ItemStack stack(String path, int count) {
            Item item = ItemIndex.byPath(path);
            return item == null ? null : new ItemStack(item, count);
        }

        /** Puts the stack into the player's inventory (the rest drops at their feet). Null stacks are ignored. */
        public static void give(ServerPlayer player, ItemStack stack) {
            if (stack != null) Delivery.toInventory(player, stack);
        }

        /** A big title in the middle of the player's screen. */
        public static void title(ServerLevel level, ServerPlayer player, String title, String subtitle) {
            String target = player.getUUID().toString();
            command(level, "title " + target + " times 10 50 15");
            if (subtitle != null && !subtitle.isEmpty()) command(level, "title " + target + " subtitle " + json(subtitle));
            command(level, "title " + target + " title " + json(title));
        }

        private static String json(String text) {
            return "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        }

        /** A lightning bolt that makes the flash and thunder but never hurts or sets anything on fire. */
        public static void lightning(ServerLevel level, double x, double y, double z) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
            if (bolt != null) {
                bolt.setPos(x, y, z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        /** Every player in the same world within the radius of this player (the player included). */
        public static List<ServerPlayer> nearby(ChatContext ctx, double radius) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : ctx.level.players()) {
                if (ctx.player.distanceToSqr(p) <= radius * radius) out.add(p);
            }
            return out;
        }
    }
}
