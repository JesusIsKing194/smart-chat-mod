package com.example.smartchat.teleport;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Finds places a player can be teleported to without dying:
 * solid ground, room for their head, no void, no lava, fire, cacti, water and so on.
 * Positions returned are the block the player's FEET will be in.
 */
public final class SafeSpot {

    static {
        Settings.describeSection("teleport", "Teleports");
    }

    public static final Setting SEARCH_RADIUS = Settings.integer("teleport", "safe_search_radius", 6, 0, 24,
            "When a destination is unsafe, the mod looks this many blocks around it for a safe spot.");

    public static final Setting MAX_TRIES = Settings.integer("teleport", "safe_search_tries", 14, 1, 64,
            "How many random places are tried for random teleports before giving up (more tries = more chunk loading).");

    private static final Set<Block> HAZARDS = new HashSet<>();
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Random RANDOM = new Random();

    static {
        HAZARDS.add(Blocks.FIRE);
        HAZARDS.add(Blocks.SOUL_FIRE);
        HAZARDS.add(Blocks.CAMPFIRE);
        HAZARDS.add(Blocks.SOUL_CAMPFIRE);
        HAZARDS.add(Blocks.MAGMA_BLOCK);
        HAZARDS.add(Blocks.CACTUS);
        HAZARDS.add(Blocks.SWEET_BERRY_BUSH);
        HAZARDS.add(Blocks.WITHER_ROSE);
        HAZARDS.add(Blocks.POWDER_SNOW);
        HAZARDS.add(Blocks.COBWEB);
        HAZARDS.add(Blocks.LAVA);
        HAZARDS.add(Blocks.TNT);
        HAZARDS.add(Blocks.POINTED_DRIPSTONE);
        HAZARDS.add(Blocks.END_PORTAL);
        HAZARDS.add(Blocks.NETHER_PORTAL);
        HAZARDS.add(Blocks.END_GATEWAY);
    }

    private SafeSpot() {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ dimension helpers

    public static boolean isNether(ServerLevel level) {
        return level.dimension().equals(Level.NETHER);
    }

    public static boolean isEnd(ServerLevel level) {
        return level.dimension().equals(Level.END);
    }

    private static int minY(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) ? -64 : 0;
    }

    private static int maxY(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) ? 320 : 256;
    }

    /** Loads (or generates) the chunk so the blocks there can be checked. */
    private static void ensureLoaded(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
    }

    // ------------------------------------------------------------------ safety check

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !HAZARDS.contains(state.getBlock());
    }

    /** True when a player standing with their feet in this block would be safe. */
    public static boolean isSafeStanding(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) return false;
        if (feet.getY() <= minY(level) + 1 || feet.getY() > maxY(level) - 3) return false;

        BlockPos head = feet.above();
        BlockPos below = feet.below();
        if (!passable(level, feet) || !passable(level, head)) return false;

        BlockState ground = level.getBlockState(below);
        if (ground.getCollisionShape(level, below).isEmpty()) return false;
        if (HAZARDS.contains(ground.getBlock())) return false;
        if (!ground.getFluidState().isEmpty()) return false;

        // no lava right next to the landing spot
        for (Direction side : SIDES) {
            if (level.getBlockState(feet.relative(side)).getBlock() == Blocks.LAVA) return false;
            if (level.getBlockState(below.relative(side)).getBlock() == Blocks.LAVA) return false;
            if (level.getBlockState(head.relative(side)).getBlock() == Blocks.LAVA) return false;
        }
        return level.getBlockState(head.above()).getBlock() != Blocks.LAVA;
    }

    // ------------------------------------------------------------------ column scans

    /** Scans one column from fromY towards toY and returns the first safe standing spot, or null. */
    public static BlockPos scanColumn(ServerLevel level, int x, int z, int fromY, int toY) {
        int step = fromY >= toY ? -1 : 1;
        for (int y = fromY; step < 0 ? y >= toY : y <= toY; y += step) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafeStanding(level, pos)) return pos;
        }
        return null;
    }

    /** Scans a column outwards from aroundY (0, +1, -1, +2, -2 ...) so the closest height wins. */
    private static BlockPos scanColumnNear(ServerLevel level, int x, int z, int aroundY, int range) {
        for (int offset = 0; offset <= range; offset++) {
            BlockPos up = new BlockPos(x, aroundY + offset, z);
            if (isSafeStanding(level, up)) return up;
            if (offset > 0) {
                BlockPos down = new BlockPos(x, aroundY - offset, z);
                if (isSafeStanding(level, down)) return down;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ finders

    /** A safe spot at or near the origin, looking at nearby columns in growing rings. Null when none is found. */
    public static BlockPos findNear(ServerLevel level, BlockPos origin, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    ensureLoaded(level, x, z);
                    BlockPos found = scanColumnNear(level, x, z, origin.getY(), 8);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * A safe spot for a random teleport in this column. In the Nether it picks a spot inside the caves
     * (never on the roof); elsewhere it uses the ground surface. Returns null if the column is unsafe.
     */
    public static BlockPos findForRandom(ServerLevel level, int x, int z) {
        ensureLoaded(level, x, z);
        if (isNether(level)) return findNetherInterior(level, x, z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return scanColumn(level, x, z, top + 1, top - 12);
    }

    /** Inside the Nether (below the bedrock roof) in this exact column, or null. */
    public static BlockPos findNetherInterior(ServerLevel level, int x, int z) {
        ensureLoaded(level, x, z);
        int start = 40 + RANDOM.nextInt(71); // 40..110
        BlockPos found = scanColumn(level, x, z, start, 32);
        if (found != null) return found;
        return scanColumn(level, x, z, start + 1, 120);
    }

    /** Looks for a spot inside the Nether near a column, trying random columns up to maxOffset blocks away. */
    public static BlockPos findNetherInteriorNear(ServerLevel level, int cx, int cz, int maxOffset) {
        BlockPos exact = findNetherInterior(level, cx, cz);
        if (exact != null) return exact;
        int tries = MAX_TRIES.asInt();
        for (int i = 0; i < tries; i++) {
            int x = cx + RANDOM.nextInt(maxOffset * 2 + 1) - maxOffset;
            int z = cz + RANDOM.nextInt(maxOffset * 2 + 1) - maxOffset;
            BlockPos found = findNetherInterior(level, x, z);
            if (found != null) return found;
        }
        return null;
    }

    /** Standing on top of the Nether's bedrock roof in this column (feet at y 128 or above), or null. */
    public static BlockPos findOnNetherRoof(ServerLevel level, int x, int z) {
        ensureLoaded(level, x, z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int from = Math.max(top, 128);
        return scanColumn(level, x, z, from, from + 3);
    }

    /** Looks for a roof spot near a column, trying nearby columns if the exact one is not usable. */
    public static BlockPos findOnNetherRoofNear(ServerLevel level, int cx, int cz) {
        BlockPos exact = findOnNetherRoof(level, cx, cz);
        if (exact != null) return exact;
        int radius = Math.max(2, SEARCH_RADIUS.asInt());
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos found = findOnNetherRoof(level, cx + dx, cz + dz);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /** True when the position is on or above the Nether roof. */
    public static boolean isOnNetherRoof(ServerLevel level, BlockPos pos) {
        return isNether(level) && pos.getY() >= 128;
    }
}
