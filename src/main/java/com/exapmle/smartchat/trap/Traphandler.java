package com.example.smartchat.trap;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.engine.Lexicon;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import com.example.smartchat.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "trap steve please": steve gets a warning, then a temporary cage forms around him.
 * Without a polite word the trap backfires and the person asking is trapped (after a shorter warning).
 */
public final class TrapHandler implements ChatRouter.Handler {

    static {
        Settings.describeSection("trap", "Traps");
    }

    public static final Setting ENABLED = Settings.bool("trap", "enabled", true,
            "Lets players trap each other in a temporary cage (\"trap steve please\").");

    public static final Setting WARNING_SECONDS = Settings.integer("trap", "warning_seconds", 10, 3, 60,
            "The target is warned this many seconds before the trap closes.");

    public static final Setting RUDE_BACKFIRE = Settings.bool("trap", "rude_backfire", true,
            "If the request is not polite, the person asking gets trapped instead. When false, they are just asked to say please.");

    public static final Setting RUDE_DELAY_SECONDS = Settings.integer("trap", "rude_delay_seconds", 5, 1, 60,
            "Seconds before a rude request's trap closes on the person who asked (also used for trapping yourself).");

    public static final Setting CAGE_SECONDS = Settings.integer("trap", "cage_seconds", 30, 5, 600,
            "How long the cage stays closed before it opens by itself.");

    public static final Setting CAGE_BLOCK = Settings.text("trap", "cage_block", "iron_bars",
            "Block the cage is made of (an item id such as iron_bars, glass, oak_fence). Only empty space is filled; existing blocks are never replaced.");

    public static final Setting REQUESTER_COOLDOWN = Settings.integer("trap", "requester_cooldown_seconds", 60, 0, 3600,
            "Seconds a player must wait between trap requests (stops spam).");

    public static final Setting TARGET_COOLDOWN = Settings.integer("trap", "target_cooldown_seconds", 120, 0, 3600,
            "Seconds before the same player can be trapped again.");

    public static final Setting MAX_ACTIVE = Settings.integer("trap", "max_active_traps", 3, 1, 20,
            "Most traps (warning or closed) allowed at the same time on the whole server.");

    public static final Setting ALLOW_SELF = Settings.bool("trap", "allow_trap_yourself", true,
            "\"trap me please\" works.");

    public static final Setting RESPECT_OPT_OUT = Settings.bool("trap", "respect_opt_out", true,
            "Players who turned traps off in their personal settings cannot be trapped.");

    public static final Setting PROTECT_ADMINS = Settings.bool("trap", "protect_admins", true,
            "Players listed in general.admin_names cannot be trapped by others.");

    public static final Setting EFFECTS = Settings.bool("trap", "effects", true,
            "Plays warning ticks, sounds and particles.");

    private static final Map<UUID, Long> REQUESTER_LAST = new HashMap<>();
    private static final Map<UUID, Long> TARGET_LAST = new HashMap<>();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static int active = 0;

    private static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "trap", "traps", "trapping", "trapped", "please", "plz", "pls", "can", "you", "could", "would", "i", "me",
            "him", "her", "them", "that", "this", "guy", "girl", "player", "in", "a", "an", "the", "cage", "put", "lock",
            "thanks", "kindly", "go", "and", "on", "for", "to", "with", "now"));

    private record Target(ServerPlayer player, boolean self) {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ entry point

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ENABLED.asBoolean()) return false;
        if (!(ctx.intents().requestCue || ctx.polite)) return false;
        if (ctx.intents().primary() != Intent.TRAP) return false;

        Target target = findTarget(ctx);
        if (target == null) return false; // not about a player (for example "it's a trap") - leave it to others

        ServerPlayer requester = ctx.player;
        UUID requesterId = requester.getUUID();
        long now = System.currentTimeMillis();

        int cooldown = REQUESTER_COOLDOWN.asInt();
        Long last = REQUESTER_LAST.get(requesterId);
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(requester, "Traps recharging... " + left + "s");
            return true;
        }
        if (active >= MAX_ACTIVE.asInt()) {
            ctx.reply("There are too many traps going on right now. Try again in a bit!");
            return true;
        }
        if (target.self() && !ALLOW_SELF.asBoolean()) {
            ctx.reply("Trapping yourself is turned off here.");
            return true;
        }

        boolean polite = ctx.polite;
        if (!polite && !target.self() && !RUDE_BACKFIRE.asBoolean()) {
            ctx.reply("Ask nicely! (say please)");
            return true;
        }

        // who ends up in the cage, and after how long
        if (target.self()) {
            if (BUSY.contains(requesterId)) {
                ctx.reply("You're already in the middle of a trap!");
                return true;
            }
            REQUESTER_LAST.put(requesterId, now);
            arm(requester, requester, RUDE_DELAY_SECONDS.asInt(), false);
            ctx.reply("Trapping you in " + RUDE_DELAY_SECONDS.asInt() + " seconds. Enjoy the view!");
            return true;
        }

        if (!polite) {
            if (BUSY.contains(requesterId)) {
                ctx.reply("You're already in the middle of a trap!");
                return true;
            }
            REQUESTER_LAST.put(requesterId, now);
            arm(requester, requester, RUDE_DELAY_SECONDS.asInt(), true);
            ctx.reply("That wasn't very polite! The trap is going to close on YOU in "
                    + RUDE_DELAY_SECONDS.asInt() + " seconds. (Say please next time.)");
            return true;
        }

        // polite request against someone else
        ServerPlayer victim = target.player();
        String victimName = victim.getName().getString();
        if (RESPECT_OPT_OUT.asBoolean() && PlayerDataStore.get(victim).trapOptOut) {
            ctx.reply(victimName + " has traps turned off.");
            return true;
        }
        if (PROTECT_ADMINS.asBoolean() && Settings.ADMIN_NAMES.asList().contains(victimName.toLowerCase())) {
            ctx.reply(victimName + " can't be trapped.");
            return true;
        }
        if (BUSY.contains(victim.getUUID())) {
            ctx.reply(victimName + " is already caught up in a trap.");
            return true;
        }
        Long targetLast = TARGET_LAST.get(victim.getUUID());
        int targetCooldown = TARGET_COOLDOWN.asInt();
        if (targetCooldown > 0 && targetLast != null && now - targetLast < targetCooldown * 1000L) {
            ctx.reply(victimName + " was trapped very recently. Give them a break!");
            return true;
        }

        REQUESTER_LAST.put(requesterId, now);
        TARGET_LAST.put(victim.getUUID(), now);
        arm(victim, requester, WARNING_SECONDS.asInt(), false);
        ctx.reply("Trap set! " + victimName + " has been warned and will be trapped in "
                + WARNING_SECONDS.asInt() + " seconds.");
        return true;
    }

    // ------------------------------------------------------------------ finding the target

    private static Target findTarget(ChatContext ctx) {
        if (ctx.has("trap me") || ctx.has("trap myself") || ctx.has("cage me")) return new Target(ctx.player, true);

        ServerPlayer best = null;
        double bestScore = 0;
        for (ServerPlayer p : ctx.level.getServer().getPlayerList().getPlayers()) {
            if (p == ctx.player) continue;
            String name = TextUtil.normalize(p.getName().getString());
            if (name.isEmpty()) continue;
            if (ctx.has(name)) return new Target(p, false);
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
        return best == null ? null : new Target(best, false);
    }

    // ------------------------------------------------------------------ the countdown and the cage

    /** Warns the victim, counts down, then closes the cage around them. */
    private static void arm(ServerPlayer victim, ServerPlayer requester, int delaySeconds, boolean backfired) {
        MinecraftServer server = victim.level().getServer();
        UUID victimId = victim.getUUID();
        String requesterName = requester.getName().getString();
        BUSY.add(victimId);
        active++;

        if (victim != requester) {
            Messages.warn(victim, requesterName + " is going to trap you in " + delaySeconds
                    + " seconds! Move away or get ready.");
        } else if (backfired) {
            Messages.warn(victim, "Your trap is closing on you in " + delaySeconds + " seconds!");
        }

        // one tick of the countdown per second
        Scheduler.repeat(0, 20, delaySeconds, step -> {
            ServerPlayer p = server.getPlayerList().getPlayer(victimId);
            if (p == null) return;
            int left = delaySeconds - step;
            Messages.actionBar(p, "Trap closing in " + left + "...");
            if (EFFECTS.asBoolean() && left <= 5) {
                ((ServerLevel) p.level()).playSound(null, p.blockPosition(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 0.6f + (5 - left) * 0.2f);
            }
        });

        Scheduler.later(delaySeconds * 20, () -> trigger(server, victimId, requesterName, backfired));
    }

    private static void trigger(MinecraftServer server, UUID victimId, String requesterName, boolean backfired) {
        ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
        if (victim == null || !victim.isAlive()) {
            finish(victimId);
            return;
        }
        ServerLevel level = (ServerLevel) victim.level();
        BlockPos feet = victim.blockPosition();

        Block cageBlock = cageBlock();
        BlockState cageState = cageBlock.defaultBlockState();
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();

        for (BlockPos pos : cageCells(feet)) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean empty = state.isAir() || state.canBeReplaced();
            if (!empty || !state.getFluidState().isEmpty()) continue; // never replace solid blocks or water
            originals.put(pos, state);
            level.setBlock(pos, cageState, 3);
        }

        if (EFFECTS.asBoolean()) {
            level.playSound(null, feet, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.CRIT, feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5,
                    30, 0.8, 1.0, 0.8, 0.2);
        }
        int seconds = CAGE_SECONDS.asInt();
        if (backfired) {
            Messages.warn(victim, "Trapped! Next time, say please. The cage opens in " + seconds + " seconds.");
        } else if (requesterName.equals(victim.getName().getString())) {
            Messages.say(victim, "You're in the cage. It opens in " + seconds + " seconds.");
        } else {
            Messages.warn(victim, requesterName + " trapped you! The cage opens in " + seconds + " seconds.");
        }

        Scheduler.later(seconds * 20, () -> release(server, victimId, level, originals, cageBlock));
    }

    private static void release(MinecraftServer server, UUID victimId, ServerLevel level,
                                Map<BlockPos, BlockState> originals, Block cageBlock) {
        BlockPos any = null;
        for (Map.Entry<BlockPos, BlockState> e : originals.entrySet()) {
            BlockPos pos = e.getKey();
            if (!level.hasChunkAt(pos)) continue;
            // only remove blocks that are still our cage (never something a player built there)
            if (level.getBlockState(pos).getBlock() == cageBlock) level.setBlock(pos, e.getValue(), 3);
            any = pos;
        }
        if (any != null && EFFECTS.asBoolean()) {
            level.playSound(null, any, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
        if (victim != null) Messages.say(victim, "The cage opens. You're free!");
        finish(victimId);
    }

    private static void finish(UUID victimId) {
        BUSY.remove(victimId);
        if (active > 0) active--;
    }

    /** A 3x3 cage around the feet: walls at foot and head height, a roof, and a floor where there is none. */
    private static List<BlockPos> cageCells(BlockPos feet) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                if (!center) {
                    cells.add(feet.offset(dx, 0, dz));
                    cells.add(feet.offset(dx, 1, dz));
                }
                cells.add(feet.offset(dx, 2, dz));
                cells.add(feet.offset(dx, -1, dz));
            }
        }
        return cells;
    }

    private static Block cageBlock() {
        Item item = ItemIndex.byPath(CAGE_BLOCK.asText().trim().replace("minecraft:", ""));
        if (item instanceof BlockItem blockItem) return blockItem.getBlock();
        return Blocks.IRON_BARS;
    }
}
