package com.example.smartchat.teleport;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.EngineSettings;
import com.example.smartchat.engine.NameIndex;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Finds biomes and structures for teleports: the nearest one, or a random one.
 * Searches run on the server thread, so they are capped by the settings below.
 */
public final class Finder {

    static {
        Settings.describeSection("teleport", "Teleports");
    }

    public static final Setting BIOME_RADIUS = Settings.integer("teleport", "biome_search_radius", 3200, 256, 10000,
            "How far (in blocks) the nearest-biome search looks. Bigger = finds rarer biomes but can lag the server.");

    public static final Setting STRUCTURE_RADIUS = Settings.integer("teleport", "structure_search_radius", 100, 10, 250,
            "How far (in chunks) the nearest-structure search looks.");

    public static final Setting RANDOM_RADIUS = Settings.integer("teleport", "random_find_radius", 3000, 200, 20000,
            "For \"random biome/structure\" requests: how far from the player the random pick may be.");

    public static final Setting RANDOM_SAMPLES = Settings.integer("teleport", "random_find_samples", 300, 20, 2000,
            "How many random points are checked when looking for a random biome.");

    public static final Setting FIND_COOLDOWN = Settings.integer("teleport", "find_cooldown_seconds", 30, 0, 3600,
            "Seconds a player must wait between biome/structure searches (they are heavy for the server).");

    /** A structure the player can ask for. One group can cover several vanilla structures (all village types). */
    public record Group(String id, String display, ResourceKey<Level> home, List<String> paths) {}

    private static final Random RANDOM = new Random();

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    private static final Map<String, String> BIOME_ALIASES = new LinkedHashMap<>();
    private static final Set<String> NETHER_BIOMES = new HashSet<>(Arrays.asList(
            "nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"));
    private static final Set<String> END_BIOMES = new HashSet<>(Arrays.asList(
            "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens"));
    private static final Map<String, Integer> BIOME_HEIGHT = new HashMap<>();

    private static final String[] BIOMES = {
            "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "cold_ocean",
            "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "river", "frozen_river", "beach", "snowy_beach",
            "stony_shore", "mushroom_fields", "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert",
            "swamp", "mangrove_swamp", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
            "dark_forest", "pale_garden", "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
            "jungle", "sparse_jungle", "bamboo_jungle", "savanna", "savanna_plateau", "windswept_savanna",
            "windswept_hills", "windswept_forest", "windswept_gravelly_hills", "badlands", "eroded_badlands",
            "wooded_badlands", "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks",
            "stony_peaks", "lush_caves", "dripstone_caves", "deep_dark", "nether_wastes", "crimson_forest",
            "warped_forest", "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands", "end_midlands",
            "small_end_islands", "end_barrens"};

    private static NameIndex<String> biomeIndex;
    private static NameIndex<String> structureIndex;

    static {
        alias("mesa", "badlands");
        alias("mushroom", "mushroom_fields");
        alias("mushroom island", "mushroom_fields");
        alias("ice spike", "ice_spikes");
        alias("cherry", "cherry_grove");
        alias("cherry forest", "cherry_grove");
        alias("mangrove", "mangrove_swamp");
        alias("dark oak forest", "dark_forest");
        alias("mountain", "jagged_peaks");
        alias("mountains", "jagged_peaks");
        alias("peaks", "jagged_peaks");
        alias("snow", "snowy_plains");
        alias("snowy", "snowy_plains");
        alias("tundra", "snowy_plains");
        alias("pine forest", "old_growth_pine_taiga");
        alias("bamboo", "bamboo_jungle");
        alias("dripstone", "dripstone_caves");
        alias("lush", "lush_caves");
        alias("sculk", "deep_dark");
        alias("warped", "warped_forest");
        alias("crimson", "crimson_forest");
        alias("soul sand", "soul_sand_valley");
        alias("basalt", "basalt_deltas");
        alias("end islands", "small_end_islands");
        alias("the end", "the_end");
        alias("sea", "ocean");

        // caves hide below the surface, so check the right height for them
        BIOME_HEIGHT.put("lush_caves", 30);
        BIOME_HEIGHT.put("dripstone_caves", 30);
        BIOME_HEIGHT.put("deep_dark", -40);

        group("village", "Village", Level.OVERWORLD,
                "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
        group("stronghold", "Stronghold", Level.OVERWORLD, "stronghold");
        group("mansion", "Woodland Mansion", Level.OVERWORLD, "mansion");
        group("monument", "Ocean Monument", Level.OVERWORLD, "monument");
        group("fortress", "Nether Fortress", Level.NETHER, "fortress");
        group("bastion", "Bastion Remnant", Level.NETHER, "bastion_remnant");
        group("end_city", "End City", Level.END, "end_city");
        group("outpost", "Pillager Outpost", Level.OVERWORLD, "pillager_outpost");
        group("ancient_city", "Ancient City", Level.OVERWORLD, "ancient_city");
        group("trail_ruins", "Trail Ruins", Level.OVERWORLD, "trail_ruins");
        group("trial_chambers", "Trial Chambers", Level.OVERWORLD, "trial_chambers");
        group("desert_temple", "Desert Temple", Level.OVERWORLD, "desert_pyramid");
        group("jungle_temple", "Jungle Temple", Level.OVERWORLD, "jungle_pyramid");
        group("igloo", "Igloo", Level.OVERWORLD, "igloo");
        group("swamp_hut", "Swamp Hut", Level.OVERWORLD, "swamp_hut");
        group("shipwreck", "Shipwreck", Level.OVERWORLD, "shipwreck", "shipwreck_beached");
        group("buried_treasure", "Buried Treasure", Level.OVERWORLD, "buried_treasure");
        group("ruined_portal", "Ruined Portal", Level.OVERWORLD,
                "ruined_portal", "ruined_portal_desert", "ruined_portal_swamp", "ruined_portal_jungle",
                "ruined_portal_mountain", "ruined_portal_ocean");
        group("ruined_portal_nether", "Nether Ruined Portal", Level.NETHER, "ruined_portal_nether");
        group("ocean_ruin", "Ocean Ruin", Level.OVERWORLD, "ocean_ruin_cold", "ocean_ruin_warm");
        group("mineshaft", "Mineshaft", Level.OVERWORLD, "mineshaft", "mineshaft_mesa");
        group("nether_fossil", "Nether Fossil", Level.NETHER, "nether_fossil");
    }

    private Finder() {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    private static void alias(String phrase, String biomePath) {
        BIOME_ALIASES.put(phrase, biomePath);
    }

    private static void group(String id, String display, ResourceKey<Level> home, String... paths) {
        GROUPS.put(id, new Group(id, display, home, Arrays.asList(paths)));
    }

    // ------------------------------------------------------------------ name matching

    private static NameIndex<String> biomes() {
        if (biomeIndex == null) {
            NameIndex<String> index = new NameIndex<>();
            for (String path : BIOMES) index.add(path, path, 50);
            for (Map.Entry<String, String> e : BIOME_ALIASES.entrySet()) index.add(e.getKey(), e.getValue(), 40);
            biomeIndex = index;
        }
        return biomeIndex;
    }

    private static NameIndex<String> structures() {
        if (structureIndex == null) {
            NameIndex<String> index = new NameIndex<>();
            for (Group g : GROUPS.values()) index.add(g.display(), g.id(), 50);
            index.add("villages", "village", 40);
            index.add("pyramid", "desert_temple", 45);
            index.add("temple", "desert_temple", 45);
            index.add("fortress", "fortress", 40);
            index.add("bastion", "bastion", 40);
            index.add("woodland mansion", "mansion", 40);
            index.add("ocean monument", "monument", 40);
            index.add("outpost", "outpost", 40);
            index.add("witch hut", "swamp_hut", 40);
            index.add("treasure", "buried_treasure", 45);
            index.add("abandoned mineshaft", "mineshaft", 40);
            index.add("ship wreck", "shipwreck", 40);
            index.add("end city", "end_city", 40);
            structureIndex = index;
        }
        return structureIndex;
    }

    /** The biome named in a (normalized) message, or null. Value = biome id path like "dark_forest". */
    public static NameIndex.Match<String> matchBiome(String normalizedText) {
        NameIndex.Match<String> m = biomes().find(normalizedText, EngineSettings.FUZZY_THRESHOLD.asDouble(), 1);
        return m != null && m.confidence() >= 0.7 ? m : null;
    }

    /** The structure named in a (normalized) message, or null. Value = group id like "village". */
    public static NameIndex.Match<String> matchStructure(String normalizedText) {
        NameIndex.Match<String> m = structures().find(normalizedText, EngineSettings.FUZZY_THRESHOLD.asDouble(), 1);
        return m != null && m.confidence() >= 0.7 ? m : null;
    }

    public static Group group(String id) {
        return GROUPS.get(id);
    }

    /** "dark_forest" -> "Dark Forest". */
    public static String prettyBiome(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ dimensions

    public static ResourceKey<Level> homeOfBiome(String path) {
        if (NETHER_BIOMES.contains(path)) return Level.NETHER;
        if (END_BIOMES.contains(path)) return Level.END;
        return Level.OVERWORLD;
    }

    public static ResourceKey<Level> homeOfStructure(String groupId) {
        Group g = GROUPS.get(groupId);
        return g == null ? Level.OVERWORLD : g.home();
    }

    /** Where to start searching in the target dimension (Nether coordinates are 1/8 of the Overworld's). */
    public static BlockPos originIn(ServerPlayer player, ServerLevel target) {
        ServerLevel from = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        if (from.dimension().equals(target.dimension())) return pos;
        if (target.dimension().equals(Level.NETHER) && from.dimension().equals(Level.OVERWORLD)) {
            return new BlockPos(pos.getX() / 8, 64, pos.getZ() / 8);
        }
        if (target.dimension().equals(Level.OVERWORLD) && from.dimension().equals(Level.NETHER)) {
            return new BlockPos(pos.getX() * 8, 64, pos.getZ() * 8);
        }
        if (target.dimension().equals(Level.END)) return new BlockPos(0, 64, 0);
        return new BlockPos(pos.getX(), 64, pos.getZ());
    }

    /** A good height to start looking for a safe spot at this column (ground surface, or 64 inside the Nether). */
    public static int surfaceY(ServerLevel level, int x, int z) {
        if (SafeSpot.isNether(level)) return 64;
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ biomes

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(path));
    }

    /** Nearest block of this biome around origin, or null when none is in range. */
    public static BlockPos nearestBiome(ServerLevel level, String biomePath, BlockPos origin) {
        ResourceKey<Biome> key = biomeKey(biomePath);
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                holder -> holder.is(key), origin, BIOME_RADIUS.asInt(), 32, 64);
        return found == null ? null : found.getFirst();
    }

    /** A random spot of this biome within the random radius, or null when none was found. */
    public static BlockPos randomBiome(ServerLevel level, String biomePath, BlockPos origin) {
        ResourceKey<Biome> key = biomeKey(biomePath);
        int radius = RANDOM_RADIUS.asInt();
        int samples = RANDOM_SAMPLES.asInt();
        int y = BIOME_HEIGHT.getOrDefault(biomePath, 64);
        List<BlockPos> hits = new ArrayList<>();
        for (int i = 0; i < samples && hits.size() < 8; i++) {
            int x = origin.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBiome(pos).is(key)) hits.add(pos);
        }
        if (!hits.isEmpty()) return hits.get(RANDOM.nextInt(hits.size()));

        // nothing hit by luck: start the "nearest" search from a random point instead
        int x = origin.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
        int z = origin.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
        return nearestBiome(level, biomePath, new BlockPos(x, y, z));
    }

    // ------------------------------------------------------------------ structures

    private static HolderSet<Structure> structureSet(ServerLevel level, Group group) {
        HolderLookup.RegistryLookup<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        for (String path : group.paths()) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(path));
            registry.get(key).ifPresent(holders::add);
        }
        return holders.isEmpty() ? null : HolderSet.direct(holders);
    }

    /** Nearest structure of this group around origin, or null when none is in range (or the dimension has none). */
    public static BlockPos nearestStructure(ServerLevel level, String groupId, BlockPos origin) {
        Group group = GROUPS.get(groupId);
        if (group == null) return null;
        HolderSet<Structure> set = structureSet(level, group);
        if (set == null) return null;
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, set, origin, STRUCTURE_RADIUS.asInt(), false);
        return found == null ? null : found.getFirst();
    }

    /** A structure found from a random starting point, so repeated requests lead to different ones. */
    public static BlockPos randomStructure(ServerLevel level, String groupId, BlockPos origin) {
        int radius = RANDOM_RADIUS.asInt();
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = origin.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            BlockPos found = nearestStructure(level, groupId, new BlockPos(x, 64, z));
            if (found != null) return found;
        }
        return null;
    }
}
