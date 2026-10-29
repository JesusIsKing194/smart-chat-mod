package com.example.smartchat.powers;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.EngineSettings;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.MobIndex;
import com.example.smartchat.engine.NameIndex;
import com.example.smartchat.teleport.SafeSpot;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.NumberWords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The "everyday powers": weather, time, flight, speed, healing, god mode, x-ray vision and mob summoning.
 * Effects and world changes go through vanilla commands, so they behave exactly like the real thing.
 */
public final class PowerHandler implements ChatRouter.Handler {

    static {
        Settings.describeSection("powers", "Powers (weather, time, flight, speed, healing, x-ray, summoning)");
    }

    public static final Setting ENABLED = Settings.bool("powers", "enabled", true,
            "Master switch for all of the powers below.");

    public static final Setting ADMIN_ONLY = Settings.list("powers", "admin_only", "",
            "Powers only admins (general.admin_names) may use. Choose from: weather, time, fly, speed, heal, god, xray, summon.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("powers", "cooldown_seconds", 3, 0, 600,
            "Seconds a player must wait between powers.");

    public static final Setting ALLOW_WEATHER = Settings.bool("powers", "allow_weather", true,
            "\"make it rain\", \"clear the weather\", \"thunderstorm\".");
    public static final Setting ALLOW_TIME = Settings.bool("powers", "allow_time", true,
            "\"make it day\", \"make it night\", \"set the time to noon\".");
    public static final Setting ALLOW_FLY = Settings.bool("powers", "allow_fly", true,
            "\"can i fly\", \"stop flying\".");
    public static final Setting ALLOW_SPEED = Settings.bool("powers", "allow_speed", true,
            "\"speed boost please\", \"stop the speed\".");
    public static final Setting ALLOW_HEAL = Settings.bool("powers", "allow_heal", true,
            "\"heal me\", \"feed me\".");
    public static final Setting ALLOW_GOD = Settings.bool("powers", "allow_god_mode", true,
            "\"god mode please\" (a timed shield: heavy damage resistance and regeneration).");
    public static final Setting ALLOW_XRAY = Settings.bool("powers", "allow_xray", true,
            "\"xray please\": night vision plus a glowing outline on nearby creatures (shown through walls).");
    public static final Setting ALLOW_SUMMON = Settings.bool("powers", "allow_summon", true,
            "\"summon 3 zombies\", \"spawn a creeper please\".");

    public static final Setting SPEED_SECONDS = Settings.integer("powers", "speed_seconds", 30, 5, 3600,
            "How long a speed boost lasts.");
    public static final Setting SPEED_LEVEL = Settings.integer("powers", "speed_level", 3, 1, 10,
            "Speed boost strength (1 = Speed I, 3 = Speed III...).");
    public static final Setting GOD_SECONDS = Settings.integer("powers", "god_mode_seconds", 60, 5, 3600,
            "How long god mode lasts.");
    public static final Setting XRAY_SECONDS = Settings.integer("powers", "xray_seconds", 60, 5, 3600,
            "How long x-ray vision lasts.");
    public static final Setting XRAY_RADIUS = Settings.integer("powers", "xray_radius", 48, 8, 128,
            "How far around the player creatures glow during x-ray vision.");
    public static final Setting SUMMON_MAX = Settings.integer("powers", "summon_max", 10, 1, 50,
            "Most mobs a player can summon in one request.");
    public static final Setting SUMMON_COOLDOWN = Settings.integer("powers", "summon_cooldown_seconds", 10, 0, 3600,
            "Seconds a player must wait between summons.");
    public static final Setting SUMMON_BLACKLIST = Settings.list("powers", "summon_blacklist",
            "ender_dragon,wither,warden,elder_guardian,giant,illusioner",
            "Mobs nobody can summon (entity ids, comma separated).");

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_SUMMON = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {
        SafeSpot.init();
    }

    // ------------------------------------------------------------------ entry point

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ENABLED.asBoolean()) return false;
        Intent intent = ctx.intents().primary();
        if (intent == null) return false;
        switch (intent) {
            case WEATHER: return weather(ctx);
            case TIME: return time(ctx);
            case FLY: return fly(ctx);
            case SPEED: return speed(ctx);
            case HEAL: return heal(ctx);
            case XRAY: return xray(ctx);
            case SUMMON: return summon(ctx);
            default: return false;
        }
    }

    // ------------------------------------------------------------------ shared helpers

    /** Returns true when the request is blocked (and the player has been told why). */
    private static boolean blocked(ChatContext ctx, String power, Setting allow) {
        if (!allow.asBoolean()) {
            ctx.reply("That power is turned off on this server.");
            return true;
        }
        if (ADMIN_ONLY.asList().contains(power) && !isAdmin(ctx.player)) {
            ctx.reply("Only admins can use that one.");
            return true;
        }
        int cooldown = COOLDOWN_SECONDS.asInt();
        Long last = LAST.get(ctx.player.getUUID());
        long now = System.currentTimeMillis();
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(ctx.player, "Recharging... " + left + "s");
            return true;
        }
        return false;
    }

    private static void used(ChatContext ctx) {
        LAST.put(ctx.player.getUUID(), System.currentTimeMillis());
    }

    private static boolean isAdmin(ServerPlayer player) {
        return Settings.ADMIN_NAMES.asList().contains(player.getName().getString().toLowerCase());
    }

    private static void run(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void effect(ServerLevel level, ServerPlayer player, String effect, int seconds, int amplifier) {
        run(level, "effect give " + player.getUUID() + " minecraft:" + effect + " " + seconds + " " + amplifier + " true");
    }

    private static void sound(ChatContext ctx, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        ctx.level.playSound(null, ctx.player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    // ------------------------------------------------------------------ weather and time

    private boolean weather(ChatContext ctx) {
        if (blocked(ctx, "weather", ALLOW_WEATHER)) return true;
        String command;
        String message;
        if (ctx.hasAny("thunder", "thunderstorm", "lightning storm")) {
            command = "weather thunder";
            message = "Thunder rolls in...";
        } else if (ctx.hasAny("stop", "clear", "sunny", "sun", "dry", "no rain", "end the rain")) {
            command = "weather clear";
            message = "The skies clear up.";
        } else {
            command = "weather rain";
            message = "Here comes the rain!";
        }
        run(ctx.level, command);
        sound(ctx, SoundEvents.WEATHER_RAIN_ABOVE, 1.0f, 1.0f);
        ctx.reply(message);
        used(ctx);
        return true;
    }

    private boolean time(ChatContext ctx) {
        if (blocked(ctx, "time", ALLOW_TIME)) return true;

        // the last time word in the message wins ("turn night into day" -> day); "skip the night" flips it
        String target = null;
        for (String token : ctx.tokens) {
            switch (token) {
                case "day": case "morning": case "daytime": target = "day"; break;
                case "noon": case "midday": target = "noon"; break;
                case "night": case "nighttime": target = "night"; break;
                case "midnight": target = "midnight"; break;
                case "sunrise": case "dawn": target = "sunrise"; break;
                case "sunset": case "dusk": case "evening": target = "sunset"; break;
                default: break;
            }
        }
        if (target == null) {
            ctx.reply("Which time? Try \"make it day\", \"make it night\", \"noon\", \"sunrise\" or \"sunset\".");
            return true;
        }
        if (ctx.has("skip")) {
            if (target.equals("night") || target.equals("midnight")) target = "day";
            else if (target.equals("day") || target.equals("noon")) target = "night";
        }

        String command;
        switch (target) {
            case "noon": command = "time set noon"; break;
            case "night": command = "time set night"; break;
            case "midnight": command = "time set midnight"; break;
            case "sunrise": command = "time set 23000"; break;
            case "sunset": command = "time set 12000"; break;
            default: command = "time set day"; break;
        }
        run(ctx.level, command);
        sound(ctx, SoundEvents.PORTAL_TRIGGER, 0.5f, 1.2f);
        ctx.reply("Time set to " + target + ".");
        used(ctx);
        return true;
    }

    // ------------------------------------------------------------------ flight, speed, healing

    private boolean fly(ChatContext ctx) {
        if (blocked(ctx, "fly", ALLOW_FLY)) return true;
        ServerPlayer player = ctx.player;
        if (player.isCreative() || player.isSpectator()) {
            ctx.reply("Your game mode already lets you fly!");
            return true;
        }
        boolean wantsOff = ctx.hasAny("stop", "off", "disable", "land", "cancel", "no more");
        boolean wantsOn = ctx.hasAny("on", "enable", "start", "let me", "can i", "i want");
        boolean enable = wantsOn && !wantsOff ? true : wantsOff && !wantsOn ? false : !player.getAbilities().mayfly;

        player.getAbilities().mayfly = enable;
        if (!enable) {
            player.getAbilities().flying = false;
            effect(ctx.level, player, "slow_falling", 10, 0); // a soft landing
        }
        player.onUpdateAbilities();
        sound(ctx, SoundEvents.ENDER_DRAGON_FLAP, 0.5f, 1.5f);
        ctx.reply(enable ? "Flight on! Double-tap jump to take off." : "Flight off. Enjoy the soft landing!");
        used(ctx);
        return true;
    }

    private boolean speed(ChatContext ctx) {
        if (blocked(ctx, "speed", ALLOW_SPEED)) return true;
        if (ctx.hasAny("stop", "off", "disable", "cancel", "remove")) {
            run(ctx.level, "effect clear " + ctx.player.getUUID() + " minecraft:speed");
            ctx.reply("Back to normal speed.");
        } else {
            effect(ctx.level, ctx.player, "speed", SPEED_SECONDS.asInt(), SPEED_LEVEL.asInt() - 1);
            sound(ctx, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
            ctx.reply("Zoom! Speed boost for " + SPEED_SECONDS.asInt() + " seconds.");
        }
        used(ctx);
        return true;
    }

    private boolean heal(ChatContext ctx) {
        ServerPlayer player = ctx.player;
        boolean god = ctx.hasAny("god mode", "godmode", "god");
        if (god) {
            if (blocked(ctx, "god", ALLOW_GOD)) return true;
            int seconds = GOD_SECONDS.asInt();
            player.heal(20.0f);
            player.getFoodData().setFoodLevel(20);
            effect(ctx.level, player, "resistance", seconds, 4);
            effect(ctx.level, player, "fire_resistance", seconds, 0);
            effect(ctx.level, player, "regeneration", seconds, 1);
            effect(ctx.level, player, "absorption", seconds, 2);
            sound(ctx, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
            ctx.reply("God mode on for " + seconds + " seconds. You're nearly untouchable!");
            used(ctx);
            return true;
        }

        if (blocked(ctx, "heal", ALLOW_HEAL)) return true;
        player.heal(20.0f);
        player.getFoodData().setFoodLevel(20);
        effect(ctx.level, player, "saturation", 1, 5);
        effect(ctx.level, player, "regeneration", 10, 1);
        sound(ctx, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
        ctx.reply("Fully healed and fed!");
        used(ctx);
        return true;
    }

    // ------------------------------------------------------------------ x-ray

    private boolean xray(ChatContext ctx) {
        if (blocked(ctx, "xray", ALLOW_XRAY)) return true;
        ServerPlayer player = ctx.player;
        int radius = XRAY_RADIUS.asInt();
        String at = "execute as " + player.getUUID() + " at @s run ";

        if (ctx.hasAny("off", "stop", "disable", "cancel")) {
            run(ctx.level, "effect clear " + player.getUUID() + " minecraft:night_vision");
            run(ctx.level, at + "effect clear @e[distance=..128,type=!minecraft:player] minecraft:glowing");
            sound(ctx, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            ctx.reply("X-ray vision off.");
            used(ctx);
            return true;
        }

        int seconds = XRAY_SECONDS.asInt();
        effect(ctx.level, player, "night_vision", seconds, 0);
        run(ctx.level, at + "effect give @e[distance=.." + radius + ",type=!minecraft:player] minecraft:glowing "
                + seconds + " 0 true");
        sound(ctx, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
        ctx.reply("X-ray vision on for " + seconds + " seconds: you can see in the dark and nearby creatures glow "
                + "through walls. (Seeing ores through stone needs a client mod, so that part isn't possible.)");
        used(ctx);
        return true;
    }

    // ------------------------------------------------------------------ summoning

    private boolean summon(ChatContext ctx) {
        NameIndex.Match<EntityType<?>> match = MobIndex.get().find(ctx.text,
                EngineSettings.FUZZY_THRESHOLD.asDouble(), 1);
        if (match == null || match.confidence() < 0.7) {
            ctx.reply("I couldn't find a mob called that. Try \"summon 3 zombies\".");
            return true;
        }
        if (blocked(ctx, "summon", ALLOW_SUMMON)) return true;

        ServerPlayer player = ctx.player;
        long now = System.currentTimeMillis();
        int cooldown = SUMMON_COOLDOWN.asInt();
        Long last = LAST_SUMMON.get(player.getUUID());
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(player, "Summoning recharging... " + left + "s");
            return true;
        }

        EntityType<?> type = match.value();
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        if (SUMMON_BLACKLIST.asList().contains(path)) {
            ctx.level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
            ctx.reply("That mob can't be summoned on this server.");
            return true;
        }

        int count = 1;
        for (String token : ctx.tokens) {
            Integer n = NumberWords.parse(token);
            if (n == null) n = NumberWords.times(token);
            if (n != null) {
                count = n;
                break;
            }
        }
        count = Math.max(1, Math.min(count, SUMMON_MAX.asInt()));

        ServerLevel level = ctx.level;
        BlockPos origin = player.blockPosition();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = 2.0 + RANDOM.nextDouble() * 2.0;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos spot = SafeSpot.findNear(level, new BlockPos(x, origin.getY(), z), 2);
            if (spot == null) spot = origin;
            run(level, "execute as " + player.getUUID() + " at @s run summon " + id + " "
                    + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() + 0.5));
            spawned++;
        }

        level.playSound(null, origin, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 1.0f);
        String name = match.key();
        ctx.reply("Summoned " + spawned + " " + name + (spawned == 1 ? "" : "s") + "!");
        LAST_SUMMON.put(player.getUUID(), now);
        used(ctx);
        return true;
    }
}
