package com.example.smartchat.portal;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * "can i plz have a nether portal": a portal frame of obsidian grows around the player block by block,
 * a harmless lightning bolt strikes, and the portal lights. It fits itself into tight spaces, replacing as few blocks as
 * possible. In the End portals cannot work, so the frame turns to crying obsidian after a dragon's roar instead.
 */
public final class PortalHandler implements ChatRouter.Handler {

    static {
        Settings.describeSection("portal", "Nether portals");
    }

    public static final Setting ENABLED = Settings.bool("portal", "enabled", true,
            "Lets players ask for a nether portal built around them (\"can i please have a nether portal\").");

    public static final Setting REQUIRE_POLITE = Settings.bool("portal", "require_polite", false,
            "Players must say please/thanks to get a portal.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("portal", "cooldown_seconds", 30, 0, 3600,
            "Seconds a player must wait between portal requests.");

    public static final Setting ANIMATION_TICKS = Settings.integer("portal", "animation_ticks", 24, 4, 100,
            "How long the obsidian frame takes to grow (20 ticks = 1 second).");

    public static final Setting LIGHTNING_DELAY_TICKS = Settings.integer("portal", "lightning_delay_ticks", 8, 0, 60,
            "Pause between the finished frame and the lightning strike.");

    public static final Setting MAX_REPLACED = Settings.integer("portal", "max_replaced_blocks", 24, 0, 100,
            "Most existing blocks the portal may replace when there is not enough room. If more would be needed, it refuses.");

    public static final Setting PROTECTED_BLOCKS = Settings.list("portal", "protected_blocks",
            "bedrock,barrier,command_block,chain_command_block,repeating_command_block,structure_block,jigsaw,"
                    + "end_portal,end_portal_frame,end_gateway,nether_portal,spawner,trial_spawner,vault,beacon,"
                    + "reinforced_deepslate",
            "Blocks the portal never replaces (any block that holds items, like chests, is always protected too).");

    public static final Setting END_RIPPLE = Settings.bool("portal", "end_ripple", true,
            "In the End: a dragon roars and the obsidian frame ripples into crying obsidian (purely decorative).");

    public static final Setting END_ROAR_VOLUME = Settings.decimal("portal", "end_roar_volume", 3.0, 0.0, 10.0,
            "Volume of the dragon's roar in the End.");

    public static final Setting END_RIPPLE_TICKS = Settings.integer("portal", "end_ripple_ticks_per_block", 6, 1, 30,
            "How slowly the crying-obsidian ripple spreads outward from the roar.");

    public static final Setting END_FRAME_SECONDS = Settings.integer("portal", "end_frame_seconds", 0, 0, 3600,
            "In the End: seconds before the decorative frame disappears and the old blocks return (0 = stays forever).");

    public static final Setting EFFECTS = Settings.bool("portal", "effects", true,
            "Plays building sounds and particles.");

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Random RANDOM = new Random();

    /** One possible placement of the frame. */
    private record Plan(Direction.Axis axis, List<BlockPos> frame, List<BlockPos> interior, double cost, int replaced) {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ entry point

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ENABLED.asBoolean()) return false;

        boolean askLike = ctx.intents().requestCue || ctx.polite;
        boolean wantsPortal = ctx.intents().primary() == Intent.PORTAL
                || (askLike && ctx.has("portal") && !ctx.hasAny("end portal", "ruined portal", "portal frame",
                "teleport", "tp", "rtp", "take me", "send me", "go to"));
        if (!wantsPortal) return false;

        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level;
        UUID id = player.getUUID();

        if (REQUIRE_POLITE.asBoolean() && !ctx.polite) {
            ctx.reply("Ask nicely and I'll build you a portal! (say please)");
            return true;
        }
        if (BUSY.contains(id)) {
            ctx.reply("Hang on, your portal is still being built!");
            return true;
        }
        int cooldown = COOLDOWN_SECONDS.asInt();
        Long last = LAST.get(id);
        long now = System.currentTimeMillis();
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(player, "Portals recharging... " + left + "s");
            return true;
        }

        BlockPos feet = player.blockPosition();
        Plan best = chooseBestPlan(level, feet, player.getDirection());
        if (best == null) {
            ctx.reply("I can't fit a portal here - something protected is in the way. Try somewhere else!");
            return true;
        }
        if (best.replaced() > MAX_REPLACED.asInt()) {
            ctx.reply("There isn't enough room here - I'd have to clear " + best.replaced()
                    + " blocks (the limit is " + MAX_REPLACED.asInt() + "). Try a roomier spot!");
            return true;
        }

        LAST.put(id, now);
        BUSY.add(id);
        boolean end = level.dimension().equals(Level.END);
        if (end) {
            ctx.reply("The End hums... a portal can't work here, but let's see what the dragon thinks.");
        } else if (best.replaced() > 0) {
            ctx.reply("Building your portal around you (clearing " + best.replaced() + " blocks to make room)...");
        } else {
            ctx.reply("Building your portal around you...");
        }
        build(level, player, feet, best, end);
        return true;
    }

    // ------------------------------------------------------------------ planning

    private static Plan chooseBestPlan(ServerLevel level, BlockPos feet, Direction facing) {
        // the player looks through the portal, so the flat side should face the way they are looking
        Direction.Axis preferred = facing.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            for (int col = 0; col <= 1; col++) {
                Plan plan = plan(level, feet, axis, col);
                if (plan == null) continue;
                double score = plan.cost() + (axis == preferred ? 0.0 : 0.05);
                if (score < bestScore) {
                    bestScore = score;
                    best = plan;
                }
            }
        }
        return best;
    }

    /**
     * A 4x5 frame (without corners) around a 2x3 opening. The player's feet are in the bottom row of the opening,
     * in column col (0 or 1). Returns null when a protected block is in the way.
     */
    private static Plan plan(ServerLevel level, BlockPos feet, Direction.Axis axis, int col) {
        int start = (axis == Direction.Axis.X ? feet.getX() : feet.getZ()) - col;
        List<BlockPos> frame = new ArrayList<>();
        List<BlockPos> interior = new ArrayList<>();
        double cost = 0;
        int replaced = 0;

        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 3; j++) {
                boolean corner = (i == -1 || i == 2) && (j == -1 || j == 3);
                if (corner) continue;
                boolean inside = i >= 0 && i <= 1 && j >= 0 && j <= 2;
                BlockPos pos = axis == Direction.Axis.X
                        ? new BlockPos(start + i, feet.getY() + j, feet.getZ())
                        : new BlockPos(feet.getX(), feet.getY() + j, start + i);
                if (!level.hasChunkAt(pos)) return null;

                BlockState state = level.getBlockState(pos);
                if (isProtected(level, pos, state)) return null;

                boolean empty = state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
                if (inside) {
                    interior.add(pos);
                    if (!empty) {
                        cost += 3.0;
                        replaced++;
                    }
                } else {
                    frame.add(pos);
                    if (state.getBlock() == Blocks.OBSIDIAN) continue;
                    if (empty) {
                        cost += 0.1;
                    } else {
                        cost += 1.0;
                        replaced++;
                    }
                }
            }
        }
        frame.sort(Comparator.comparingInt(BlockPos::getY)
                .thenComparingInt(p -> axis == Direction.Axis.X ? p.getX() : p.getZ()));
        return new Plan(axis, frame, interior, cost, replaced);
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return PROTECTED_BLOCKS.asList().contains(path);
    }

    // ------------------------------------------------------------------ building

    private static void build(ServerLevel level, ServerPlayer player, BlockPos feet, Plan plan, boolean end) {
        UUID id = player.getUUID();
        List<BlockPos> frame = plan.frame();

        // remember what was there (used when the End frame fades away)
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (BlockPos pos : frame) originals.put(pos, level.getBlockState(pos));
        for (BlockPos pos : plan.interior()) originals.put(pos, level.getBlockState(pos));

        // centre of the opening
        int openingStart = startOf(plan);
        final double centerX = plan.axis() == Direction.Axis.X ? openingStart + 1.0 : feet.getX() + 0.5;
        final double centerZ = plan.axis() == Direction.Axis.X ? feet.getZ() + 0.5 : openingStart + 1.0;
        final double centerY = feet.getY() + 1.0;

        // 1. clear the opening straight away
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : plan.interior()) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, 3);
        }

        // 2. the obsidian frame grows from the bottom up
        int total = frame.size();
        int ticks = ANIMATION_TICKS.asInt();
        int perTick = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, ticks)));
        int steps = (int) Math.ceil(total / (double) perTick);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();

        Scheduler.repeat(2, 1, steps, step -> {
            for (int k = 0; k < perTick; k++) {
                int index = step * perTick + k;
                if (index >= total) break;
                BlockPos pos = frame.get(index);
                level.setBlock(pos, obsidian, 3);
                if (EFFECTS.asBoolean()) {
                    level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f,
                            0.7f + RANDOM.nextFloat() * 0.4f);
                    level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            8, 0.3, 0.3, 0.3, 0.2);
                }
            }
        });

        int frameDone = 2 + steps;

        if (end) {
            endSequence(level, plan, originals, centerX, centerY, centerZ, frameDone, id);
        } else {
            overworldSequence(level, plan, centerX, feet.getY(), centerZ, frameDone, id);
        }
    }

    /** The first block of the opening along the frame's axis. */
    private static int startOf(Plan plan) {
        // the opening is plan.interior(): its smallest coordinate along the axis
        int min = Integer.MAX_VALUE;
        for (BlockPos pos : plan.interior()) {
            int c = plan.axis() == Direction.Axis.X ? pos.getX() : pos.getZ();
            if (c < min) min = c;
        }
        return min;
    }

    private static void overworldSequence(ServerLevel level, Plan plan, double cx, int feetY, double cz,
                                          int frameDone, UUID id) {
        int strikeAt = frameDone + LIGHTNING_DELAY_TICKS.asInt();

        // harmless lightning
        Scheduler.later(strikeAt, () -> {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
            if (bolt != null) {
                bolt.setPos(cx, feetY, cz);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        });

        // light the portal
        Scheduler.later(strikeAt + 4, () -> {
            BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_AXIS, plan.axis());
            for (BlockPos pos : plan.interior()) level.setBlock(pos, portal, 2);
            if (EFFECTS.asBoolean()) {
                level.playSound(null, BlockPos.containing(cx, feetY, cz), SoundEvents.PORTAL_TRIGGER,
                        SoundSource.BLOCKS, 0.6f, 1.2f);
            }
        });

        Scheduler.later(strikeAt + 6, () -> BUSY.remove(id));
    }

    private static void endSequence(ServerLevel level, Plan plan, Map<BlockPos, BlockState> originals,
                                    double cx, double cy, double cz, int frameDone, UUID id) {
        if (!END_RIPPLE.asBoolean()) {
            Scheduler.later(frameDone + 2, () -> BUSY.remove(id));
            return;
        }
        int roarAt = frameDone + 10;
        int rippleBase = roarAt + 2;
        int perBlock = END_RIPPLE_TICKS.asInt();

        // the dragon roars...
        Scheduler.later(roarAt, () -> level.playSound(null, BlockPos.containing(cx, cy, cz),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, (float) END_ROAR_VOLUME.asDouble(), 1.0f));

        // ...and the roar ripples outward, turning the obsidian to crying obsidian
        int lastDelay = 0;
        for (BlockPos pos : plan.frame()) {
            double dx = pos.getX() + 0.5 - cx;
            double dy = pos.getY() + 0.5 - cy;
            double dz = pos.getZ() + 0.5 - cz;
            int delay = rippleBase + (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * perBlock);
            lastDelay = Math.max(lastDelay, delay);
            Scheduler.later(delay, () -> {
                if (level.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) return;
                level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
                if (EFFECTS.asBoolean()) {
                    level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            14, 0.4, 0.4, 0.4, 0.3);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 0.6f);
                }
            });
        }

        int finishAt = lastDelay + 10;
        int fadeSeconds = END_FRAME_SECONDS.asInt();
        if (fadeSeconds > 0) {
            Scheduler.later(finishAt + fadeSeconds * 20, () -> {
                for (Map.Entry<BlockPos, BlockState> e : originals.entrySet()) {
                    BlockPos pos = e.getKey();
                    Block current = level.getBlockState(pos).getBlock();
                    // only undo blocks that are still ours
                    if (current == Blocks.OBSIDIAN || current == Blocks.CRYING_OBSIDIAN) {
                        level.setBlock(pos, e.getValue(), 3);
                    }
                }
            });
        }
        Scheduler.later(finishAt, () -> BUSY.remove(id));
    }
}
