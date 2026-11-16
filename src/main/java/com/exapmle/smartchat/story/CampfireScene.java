package com.example.smartchat.story;

import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.teleport.SafeSpot;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The visuals of a campfire story: a campfire in front of the storyteller, log seats around it (oak, or the wood
 * of the local biome), everyone sitting on a log, and optionally night. tearDown() puts everything back.
 */
public final class CampfireScene {

    private final ServerLevel level;
    private final String dimensionId;
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private final Map<BlockPos, Block> placed = new LinkedHashMap<>();
    private long savedTime = -1;
    public final BlockPos fire;

    private CampfireScene(ServerLevel level, BlockPos fire) {
        this.level = level;
        this.fire = fire;
        if (level.dimension().equals(Level.NETHER)) this.dimensionId = "minecraft:the_nether";
        else if (level.dimension().equals(Level.END)) this.dimensionId = "minecraft:the_end";
        else this.dimensionId = "minecraft:overworld";
    }

    /** Builds the scene, or returns null when there is no good spot in front of the storyteller. */
    public static CampfireScene build(ServerLevel level, ServerPlayer starter, List<ServerPlayer> players, boolean night) {
        Direction facing = starter.getDirection();
        BlockPos fire = SafeSpot.findNear(level, starter.blockPosition().relative(facing, 4), 2);
        if (fire == null) return null;

        CampfireScene scene = new CampfireScene(level, fire);
        scene.place(fire, Blocks.CAMPFIRE.defaultBlockState());
        Block log = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(woodFor(level, fire)));

        int seats = Math.min(players.size(), 8);
        for (int i = 0; i < seats; i++) {
            double angle = Math.PI * 2 * i / seats;
            int sx = fire.getX() + (int) Math.round(Math.cos(angle) * 2.5);
            int sz = fire.getZ() + (int) Math.round(Math.sin(angle) * 2.5);
            BlockPos spot = SafeSpot.findNear(level, new BlockPos(sx, fire.getY(), sz), 1);
            if (spot == null || spot.equals(fire) || scene.placed.containsKey(spot)) continue;

            BlockState state = log.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                boolean alongZ = Math.abs(sx - fire.getX()) > Math.abs(sz - fire.getZ());
                state = state.setValue(BlockStateProperties.AXIS, alongZ ? Direction.Axis.Z : Direction.Axis.X);
            }
            scene.place(spot, state);
            scene.seat(players.get(i), spot);
        }

        if (night) {
            scene.savedTime = level.getDayTime();
            Tools.command(level, "time set night");
        }
        return scene;
    }

    private void place(BlockPos pos, BlockState state) {
        originals.put(pos, level.getBlockState(pos));
        level.setBlock(pos, state, 3);
        placed.put(pos, state.getBlock());
    }

    /** An invisible armor stand on top of the log, and the player rides it. */
    private void seat(ServerPlayer player, BlockPos log) {
        String tag = "sc_" + player.getUUID().toString().substring(0, 8);
        String as = "execute as " + player.getUUID() + " at @s run ";
        Tools.command(level, as + "summon minecraft:armor_stand " + (log.getX() + 0.5) + " " + (log.getY() + 0.85) + " "
                + (log.getZ() + 0.5) + " {Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"smartchat_seat\",\"" + tag + "\"]}");
        Scheduler.later(5, () -> Tools.command(level, as + "ride @s mount @e[type=minecraft:armor_stand,tag=" + tag
                + ",limit=1,sort=nearest]"));
    }

    /** Removes the campfire, logs, seats and brings the old time of day back. */
    public void tearDown() {
        Tools.command(level, "execute in " + dimensionId + " run kill @e[type=minecraft:armor_stand,tag=smartchat_seat]");
        for (Map.Entry<BlockPos, BlockState> e : originals.entrySet()) {
            BlockPos pos = e.getKey();
            if (!level.hasChunkAt(pos)) continue;
            // only remove blocks that are still ours
            if (level.getBlockState(pos).getBlock() == placed.get(pos)) level.setBlock(pos, e.getValue(), 3);
        }
        originals.clear();
        placed.clear();
        if (savedTime >= 0) {
            Tools.command(level, "time set " + (savedTime % 24000L));
            savedTime = -1;
        }
    }

    /** The log type that matches the biome: oak by default, spruce in the cold, birch, jungle, acacia and so on. */
    private static String woodFor(ServerLevel level, BlockPos pos) {
        String biome = "";
        try {
            Holder<Biome> holder = level.getBiome(pos);
            biome = holder.getRegisteredName().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
        }
        if (biome.contains("crimson")) return "crimson_stem";
        if (biome.contains("warped")) return "warped_stem";
        if (biome.contains("pale")) return "pale_oak_log";
        if (biome.contains("dark_forest")) return "dark_oak_log";
        if (biome.contains("birch")) return "birch_log";
        if (biome.contains("jungle") || biome.contains("bamboo")) return "jungle_log";
        if (biome.contains("savanna") || biome.contains("badlands")) return "acacia_log";
        if (biome.contains("cherry")) return "cherry_log";
        if (biome.contains("mangrove")) return "mangrove_log";
        if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("grove") || biome.contains("frozen")
                || biome.contains("peaks") || biome.contains("ice")) return "spruce_log";
        return "oak_log";
    }

    /** Used by the story engine to list who is seated (kept for completeness). */
    public List<String> info() {
        return new ArrayList<>(List.of("fire at " + fire.getX() + ", " + fire.getY() + ", " + fire.getZ()));
    }
}
