package com.example.smartchat.teleport;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.Lexicon;
import com.example.smartchat.engine.NameIndex;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import com.example.smartchat.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Every kind of teleport request:
 * "teleport me plz", "rtp", "tp me to steve", "take me to a desert", "teleport me to the nearest village",
 * "random village", "can i go to the nether roof plz", "down from the roof".
 * All landings are checked by SafeSpot so nobody is dropped into the void or lava.
 */
public final class TeleportHandler implements ChatRouter.Handler {

    static {
        Settings.describeSection("teleport", "Teleports");
    }

    public static final Setting ENABLED = Settings.bool("teleport", "enabled", true,
            "Lets players teleport by asking in chat.");

    public static final Setting REQUIRE_POLITE = Settings.bool("teleport", "require_polite", false,
            "Players must say please/thanks to teleport.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("teleport", "cooldown_seconds", 5, 0, 3600,
            "Seconds a player must wait between teleports.");

    public static final Setting RTP_RADIUS = Settings.integer("teleport", "rtp_radius", 500, 50, 30000,
            "Random teleports land within this many blocks of where the player is standing.");

    public static final Setting RTP_MIN_DISTANCE = Settings.integer("teleport", "rtp_min_distance", 100, 0, 5000,
            "Random teleports land at least this far away (must be smaller than the radius).");

    public static final Setting ALLOW_PLAYER_TP = Settings.bool("teleport", "allow_teleport_to_players", true,
            "Players may teleport to other online players (\"tp me to steve\").");

    public static final Setting NOTIFY_TARGET = Settings.bool("teleport", "notify_target_player", true,
            "Tell a player when someone teleports to them.");

    public static final Setting ALLOW_CROSS_DIMENSION = Settings.bool("teleport", "allow_other_dimensions", true,
            "Biome and structure teleports may send players to another dimension (a Nether fortress, an End city...).");

    public static final Setting ALLOW_ROOF = Settings.bool("teleport", "allow_nether_roof", true,
            "Lets players go to the Nether roof and back down.");

    public static final Setting EFFECTS = Settings.bool("teleport", "effects", true,
            "Plays teleport sounds and particles at both ends.");

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIND = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** Biomes that are underground, so the search keeps the height Minecraft found. */
    private static final Set<String> CAVE_BIOMES = new HashSet<>(Arrays.asList(
            "lush_caves", "dripstone_caves", "deep_dark"));

    /** Words that are never a player name or a place. */
    private static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "tp", "teleport", "teleporting", "me", "to", "please", "plz", "pls", "random", "rtp", "nearest", "closest",
            "near", "nearby", "close", "the", "a", "an", "go", "take", "send", "bring", "can", "i", "you", "could",
            "would", "player", "someone", "anyone", "somewhere", "anywhere", "new", "place", "spot", "location",
            "away", "far", "safe", "any", "biome", "structure", "warp", "surprise", "wherever", "whatever", "thanks"));

    private static final String[] OVERWORLD_STRUCTURES = {"village", "mansion", "monument", "outpost", "ancient_city",
            "trail_ruins", "trial_chambers", "desert_temple", "jungle_temple", "igloo", "swamp_hut", "shipwreck",
            "ruined_portal", "ocean_ruin", "mineshaft", "stronghold"};
    private static final String[] NETHER_STRUCTURES = {"fortress", "bastion", "nether_fossil", "ruined_portal_nether"};
    private static final String[] END_STRUCTURES = {"end_city"};

    private record Destination(ServerLevel level, BlockPos spot, String display) {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {
        SafeSpot.init();
        Finder.init();
    }

    // ------------------------------------------------------------------ entry point

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ENABLED.asBoolean()) return false;

        boolean askLike = ctx.intents().requestCue || ctx.polite;
        boolean roofWords = askLike && (ctx.has("roof") || ctx.has("nether ceiling") || ctx.has("bedrock ceiling"));
        if (!roofWords && ctx.intents().primary() != Intent.TELEPORT) return false;

        if (REQUIRE_POLITE.asBoolean() && !ctx.polite) {
            ctx.reply("Ask nicely and I'll teleport you! (say please)");
            return true;
        }
        if (onCooldown(ctx)) return true;

        if (roofWords) return roof(ctx);

        // 1. another player
        if (ALLOW_PLAYER_TP.asBoolean() || ctx.hasAny("random player", "someone", "anyone")) {
            ServerPlayer target = findPlayer(ctx);
            if (target != null) return toPlayer(ctx, target);
            if (ctx.hasAny("random player", "someone", "anyone", "a player", "another player")) {
                return toRandomPlayer(ctx);
            }
        }

        // 2. a named biome or structure
        NameIndex.Match<String> structure = Finder.matchStructure(ctx.text);
        NameIndex.Match<String> biome = Finder.matchBiome(ctx.text);
        boolean nearest = ctx.hasAny("nearest", "closest", "near", "nearby", "close");
        boolean random = !nearest && ctx.hasAny("random", "any", "surprise", "wherever", "whatever", "anywhere", "somewhere");

        if (structure != null || biome != null) {
            boolean useStructure;
            if (structure == null) useStructure = false;
            else if (biome == null) useStructure = true;
            else if (structure.confidence() != biome.confidence()) useStructure = structure.confidence() > biome.confidence();
            else useStructure = structure.key().length() >= biome.key().length();

            if (useStructure) {
                Finder.Group group = Finder.group(structure.value());
                return place(ctx, true, structure.value(), group == null ? structure.value() : group.display(), random);
            }
            return place(ctx, false, biome.value(), Finder.prettyBiome(biome.value()), random);
        }

        // 3. "random structure" without a name
        if (ctx.has("structure")) return randomStructure(ctx);

        // 4. something we could not understand ("tp to the moon")
        if (hasUnknownTarget(ctx)) {
            ctx.reply("I couldn't find that place or player. Try a biome (\"desert\"), a structure (\"village\"), "
                    + "a player name, \"the nether roof\", or just \"rtp\".");
            return true;
        }

        // 5. plain "teleport me" / "rtp" / "random teleport"
        return randomTeleport(ctx);
    }

    // ------------------------------------------------------------------ random teleport

    private static boolean randomTeleport(ChatContext ctx) {
        ServerLevel level = ctx.level;
        BlockPos origin = ctx.player.blockPosition();
        int radius = RTP_RADIUS.asInt();
        int min = Math.min(RTP_MIN_DISTANCE.asInt(), Math.max(0, radius - 1));

        BlockPos spot = null;
        int tries = SafeSpot.MAX_TRIES.asInt();
        for (int i = 0; i < tries && spot == null; i++) {
            int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            if (Math.max(Math.abs(dx), Math.abs(dz)) < min) continue;
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, origin.getY(), z))) continue;
            spot = SafeSpot.findForRandom(level, x, z);
        }
        if (spot == null) {
            ctx.reply("I couldn't find a safe place to land. Try again in a moment!");
            return true;
        }
        go(ctx, level, spot, "Whoosh! Teleported to " + coords(spot) + ".");
        return true;
    }

    // ------------------------------------------------------------------ players

    private static List<ServerPlayer> others(ChatContext ctx) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : ctx.level.getServer().getPlayerList().getPlayers()) {
            if (p != ctx.player) out.add(p);
        }
        return out;
    }

    private static ServerPlayer findPlayer(ChatContext ctx) {
        ServerPlayer best = null;
        double bestScore = 0;
        for (ServerPlayer p : others(ctx)) {
            String name = TextUtil.normalize(p.getName().getString());
            if (name.isEmpty()) continue;
            if (ctx.has(name)) return p;
            for (String token : ctx.tokens) {
                if (token.length() < 3 || IGNORE.contains(token) || Lexicon.STOP.contains(token)) continue;
                double score = 0;
                if (token.length() >= 4 && name.startsWith(token)) score = 0.9;
                else if (token.length() >= 4 && name.length() >= 4 && TextUtil.similarity(token, name) >= 0.8) {
                    score = TextUtil.similarity(token, name);
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    private static boolean toPlayer(ChatContext ctx, ServerPlayer target) {
        if (!ALLOW_PLAYER_TP.asBoolean()) {
            ctx.reply("Teleporting to players is turned off on this server.");
            return true;
        }
        ServerLevel targetLevel = (ServerLevel) target.level();
        BlockPos spot = SafeSpot.findNear(targetLevel, target.blockPosition(), SafeSpot.SEARCH_RADIUS.asInt());
        if (spot == null) {
            ctx.reply("I couldn't find a safe spot next to " + target.getName().getString() + " - try again in a moment.");
            return true;
        }
        go(ctx, targetLevel, spot, "Teleported to " + target.getName().getString() + "!");
        if (NOTIFY_TARGET.asBoolean()) {
            Messages.say(target, ctx.player.getName().getString() + " teleported to you.");
        }
        return true;
    }

    private static boolean toRandomPlayer(ChatContext ctx) {
        List<ServerPlayer> list = others(ctx);
        if (list.isEmpty()) {
            ctx.reply("You're the only one online right now!");
            return true;
        }
        return toPlayer(ctx, list.get(RANDOM.nextInt(list.size())));
    }

    // ------------------------------------------------------------------ biomes and structures

    private static boolean place(ChatContext ctx, boolean structure, String id, String display, boolean random) {
        if (findOnCooldown(ctx)) return true;
        LAST_FIND.put(ctx.player.getUUID(), System.currentTimeMillis());

        Destination dest = findPlace(ctx, structure, id, display, random, false);
        if (dest == null) return true;
        String how = random ? "a random " : "the nearest ";
        go(ctx, dest.level(), dest.spot(), "Teleported to " + how + dest.display() + " at " + coords(dest.spot()) + "!");
        return true;
    }

    private static boolean randomStructure(ChatContext ctx) {
        if (findOnCooldown(ctx)) return true;
        LAST_FIND.put(ctx.player.getUUID(), System.currentTimeMillis());

        ServerLevel level = ctx.level;
        String[] pool = SafeSpot.isNether(level) ? NETHER_STRUCTURES : SafeSpot.isEnd(level) ? END_STRUCTURES : OVERWORLD_STRUCTURES;
        List<String> ids = new ArrayList<>(Arrays.asList(pool));
        for (int attempt = 0; attempt < 3 && !ids.isEmpty(); attempt++) {
            String id = ids.remove(RANDOM.nextInt(ids.size()));
            Finder.Group group = Finder.group(id);
            String display = group == null ? id : group.display();
            Destination dest = findPlace(ctx, true, id, display, true, true);
            if (dest != null) {
                go(ctx, dest.level(), dest.spot(), "Teleported to a random " + dest.display() + " at " + coords(dest.spot()) + "!");
                return true;
            }
        }
        ctx.reply("I couldn't find a structure nearby this time. Try again or name one, like \"village\".");
        return true;
    }

    /** Finds a place and a safe spot in it. Returns null (and tells the player unless quiet) when that fails. */
    private static Destination findPlace(ChatContext ctx, boolean structure, String id, String display,
                                         boolean random, boolean quiet) {
        MinecraftServer server = ctx.level.getServer();
        ResourceKey<Level> home = structure ? Finder.homeOfStructure(id) : Finder.homeOfBiome(id);
        ServerLevel dest = server.getLevel(home);
        if (dest == null) {
            if (!quiet) ctx.reply("That place isn't available on this server.");
            return null;
        }
        boolean sameDimension = dest.dimension().equals(ctx.level.dimension());
        if (!sameDimension && !ALLOW_CROSS_DIMENSION.asBoolean()) {
            if (!quiet) ctx.reply("A " + display + " is in another dimension, and travelling between dimensions is turned off.");
            return null;
        }
        if (!quiet) ctx.reply("Looking for " + (random ? "a random " : "the nearest ") + display + "...");

        BlockPos origin = Finder.originIn(ctx.player, dest);
        BlockPos found;
        if (structure) {
            found = random ? Finder.randomStructure(dest, id, origin) : Finder.nearestStructure(dest, id, origin);
        } else {
            found = random ? Finder.randomBiome(dest, id, origin) : Finder.nearestBiome(dest, id, origin);
        }
        if (found == null) {
            if (!quiet) ctx.reply("I couldn't find a " + display + " within range. Try again, or try a different one.");
            return null;
        }

        boolean heightKnown = !structure && (CAVE_BIOMES.contains(id) || SafeSpot.isNether(dest));
        BlockPos spot = locateSafe(dest, found, heightKnown);
        if (spot == null) {
            if (!quiet) {
                ctx.reply("I found a " + display + " but couldn't find a safe place to stand there "
                        + "(it may be deep underwater or buried), so I kept you where you are.");
            }
            return null;
        }
        return new Destination(dest, spot, display);
    }

    private static BlockPos locateSafe(ServerLevel level, BlockPos found, boolean heightKnown) {
        int x = found.getX();
        int z = found.getZ();
        int radius = SafeSpot.SEARCH_RADIUS.asInt() + 8;

        if (SafeSpot.isNether(level)) {
            if (heightKnown) {
                BlockPos near = SafeSpot.findNear(level, found, radius);
                if (near != null) return near;
            }
            return SafeSpot.findNetherInteriorNear(level, x, z, 24);
        }
        if (heightKnown) {
            BlockPos near = SafeSpot.findNear(level, found, radius);
            if (near != null) return near;
        }
        BlockPos ground = SafeSpot.findForRandom(level, x, z);
        if (ground != null) return ground;
        BlockPos surface = new BlockPos(x, Finder.surfaceY(level, x, z), z);
        BlockPos near = SafeSpot.findNear(level, surface, radius);
        if (near != null) return near;
        // last try for places like ocean structures: look for dry land a bit further away
        return SafeSpot.findNear(level, surface, 32);
    }

    // ------------------------------------------------------------------ nether roof

    private static boolean roof(ChatContext ctx) {
        if (!ALLOW_ROOF.asBoolean()) {
            ctx.reply("The Nether roof is off limits on this server.");
            return true;
        }
        ServerLevel level = ctx.level;
        if (!SafeSpot.isNether(level)) {
            ctx.reply("The Nether roof only exists in the Nether - go there first!");
            return true;
        }
        BlockPos here = ctx.player.blockPosition();
        boolean down = ctx.hasAny("down", "off", "leave", "exit", "below", "under", "return", "get down", "come down");

        if (down) {
            if (!SafeSpot.isOnNetherRoof(level, here)) {
                ctx.reply("You're not on the roof - you're already down below.");
                return true;
            }
            BlockPos spot = SafeSpot.findNetherInteriorNear(level, here.getX(), here.getZ(), 48);
            if (spot == null) {
                ctx.reply("I couldn't find a safe cave below you. Try again in a moment!");
                return true;
            }
            go(ctx, level, spot, "Back down below the roof at " + coords(spot) + ".");
            return true;
        }

        if (SafeSpot.isOnNetherRoof(level, here)) {
            ctx.reply("You're already on the roof! Say \"down from the roof\" to come back down.");
            return true;
        }
        BlockPos spot = SafeSpot.findOnNetherRoofNear(level, here.getX(), here.getZ());
        if (spot == null) {
            ctx.reply("I couldn't find a safe spot up on the roof. Try again in a moment!");
            return true;
        }
        go(ctx, level, spot, "Welcome to the Nether roof! Say \"down from the roof\" when you want to come back.");
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean hasUnknownTarget(ChatContext ctx) {
        int idx = ctx.tokens.indexOf("to");
        if (idx < 0) return false;
        for (int i = idx + 1; i < ctx.tokens.size(); i++) {
            String w = ctx.tokens.get(i);
            if (!IGNORE.contains(w) && !Lexicon.STOP.contains(w)) return true;
        }
        return false;
    }

    private static boolean onCooldown(ChatContext ctx) {
        int cooldown = COOLDOWN_SECONDS.asInt();
        Long last = LAST.get(ctx.player.getUUID());
        long now = System.currentTimeMillis();
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(ctx.player, "Teleport recharging... " + left + "s");
            return true;
        }
        return false;
    }

    private static boolean findOnCooldown(ChatContext ctx) {
        int cooldown = Finder.FIND_COOLDOWN.asInt();
        Long last = LAST_FIND.get(ctx.player.getUUID());
        long now = System.currentTimeMillis();
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(ctx.player, "Searches recharging... " + left + "s");
            return true;
        }
        return false;
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String dimensionId(ServerLevel level) {
        if (level.dimension().equals(Level.NETHER)) return "minecraft:the_nether";
        if (level.dimension().equals(Level.END)) return "minecraft:the_end";
        return "minecraft:overworld";
    }

    /** Teleports the player (through the vanilla tp command, so every dimension works) and plays the effects. */
    private static void go(ChatContext ctx, ServerLevel dest, BlockPos feet, String message) {
        ServerPlayer player = ctx.player;
        ServerLevel from = ctx.level;
        MinecraftServer server = from.getServer();

        effects(from, player.blockPosition());

        String position = (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5);
        String command;
        if (dest.dimension().equals(from.dimension())) {
            command = "execute as " + player.getUUID() + " at @s run tp @s " + position;
        } else {
            command = "execute in " + dimensionId(dest) + " run tp " + player.getUUID() + " " + position;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);

        LAST.put(player.getUUID(), System.currentTimeMillis());
        Scheduler.later(1, () -> effects(dest, feet));
        ctx.reply(message);
    }

    private static void effects(ServerLevel level, BlockPos pos) {
        if (!EFFECTS.asBoolean()) return;
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                40, 0.5, 1.0, 0.5, 0.3);
    }
}
