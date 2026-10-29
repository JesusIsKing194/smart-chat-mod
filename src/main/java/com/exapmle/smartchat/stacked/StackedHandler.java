package com.example.smartchat.stacked;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** "can i get stacked please" -> loot boxes. Rude version -> TNT and anvils. */
public final class StackedHandler implements ChatRouter.Handler {

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Random RANDOM = new Random();

    @Override
    public boolean handle(ChatContext ctx) {
        if (!StackedSettings.ENABLED.asBoolean()) return false;
        if (!ctx.intents().requestCue && !ctx.polite) return false;
        if (ctx.intents().primary() != Intent.STACKED) return false;

        UUID id = ctx.player.getUUID();
        long now = System.currentTimeMillis();
        int cooldown = StackedSettings.COOLDOWN_SECONDS.asInt();
        Long last = LAST.get(id);
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(ctx.player, "Slow down! Try again in " + left + "s");
            return true;
        }

        if (ctx.polite) {
            LAST.put(id, now);
            gift(ctx);
        } else if (StackedSettings.PUNISH.asBoolean()) {
            LAST.put(id, now);
            punish(ctx);
        } else {
            ctx.reply("Ask nicely and I'll stack you up! (say please)");
        }
        return true;
    }

    // ------------------------------------------------------------------ gift

    private static void gift(ChatContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level;
        List<LootBoxes.Box> boxes = LootBoxes.build(level);

        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();
        BlockPos front = player.blockPosition().relative(facing, StackedSettings.DISTANCE.asInt());
        int height = StackedSettings.BOX_HEIGHT.asInt();
        int middle = boxes.size() / 2;

        boolean anyInventory = false;
        for (int i = 0; i < boxes.size(); i++) {
            LootBoxes.Box box = boxes.get(i);
            BlockPos column = front.relative(right, i - middle);
            BlockPos landing = findLanding(level, column, player.blockPosition().getY());
            if (landing == null || !Delivery.columnClear(level, landing.getX(), landing.getY(), landing.getZ(), height)) {
                Delivery.toInventory(player, LootBoxes.asItem(box));
                anyInventory = true;
                continue;
            }
            dropBox(player, level, box, landing, height, i * 4);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        BlockPos above = player.blockPosition().above(3);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, above.getX(), above.getY(), above.getZ(), 30, 1.0, 0.5, 1.0, 0.1);
        ctx.reply(anyInventory
                ? "Stacked! Your loot is landing in front of you (anything blocked went into your inventory)."
                : "Stacked! Your loot is landing in front of you.");
    }

    private static BlockPos findLanding(ServerLevel level, BlockPos column, int playerY) {
        for (int dy = 2; dy >= -5; dy--) {
            BlockPos pos = new BlockPos(column.getX(), playerY + dy, column.getZ());
            if (!level.hasChunkAt(pos)) return null;
            BlockState here = level.getBlockState(pos);
            BlockPos belowPos = pos.below();
            BlockState under = level.getBlockState(belowPos);
            if (here.getCollisionShape(level, pos).isEmpty() && here.getFluidState().isEmpty()
                    && !under.getCollisionShape(level, belowPos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /** The box falls as a block, then its contents are filled in once it has landed. */
    private static void dropBox(ServerPlayer player, ServerLevel level, LootBoxes.Box box, BlockPos landing, int height, int delay) {
        Item boxItem = ItemIndex.byPath(box.boxItemId());
        if (!(boxItem instanceof BlockItem blockItem)) {
            Delivery.toInventory(player, LootBoxes.asItem(box));
            return;
        }
        Block block = blockItem.getBlock();
        Scheduler.later(delay, () -> {
            FallingBlockEntity entity = FallingBlockEntity.fall(level, landing.above(height), block.defaultBlockState());
            if (entity != null) {
                entity.time = 1;
                entity.dropItem = true;
            }
            Scheduler.Handle[] holder = new Scheduler.Handle[1];
            holder[0] = Scheduler.repeat(15, 5, 30, step -> {
                BlockEntity be = level.getBlockEntity(landing);
                if (be instanceof Container container && level.getBlockState(landing).getBlock() == block) {
                    List<net.minecraft.world.item.ItemStack> contents = box.contents();
                    for (int s = 0; s < contents.size() && s < container.getContainerSize(); s++) {
                        container.setItem(s, contents.get(s).copy());
                    }
                    container.setChanged();
                    if (holder[0] != null) holder[0].cancel();
                } else if (step == 29) {
                    Delivery.toInventory(player, LootBoxes.asItem(box));
                }
            });
        });
    }

    // ------------------------------------------------------------------ punishment

    private static void punish(ChatContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level;
        BlockPos origin = player.blockPosition();
        int radius = StackedSettings.PUNISH_RADIUS.asInt();
        int height = StackedSettings.PUNISH_HEIGHT.asInt();

        level.playSound(null, origin, SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
        ctx.reply("That wasn't very polite... brace yourself!");

        for (int i = 0; i < StackedSettings.TNT_COUNT.asInt(); i++) {
            int x = origin.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int h = clearHeight(level, x, origin.getY(), z, height);
            if (h < 3) continue;
            Scheduler.later(RANDOM.nextInt(30), () -> {
                PrimedTnt tnt = new PrimedTnt(level, x + 0.5, origin.getY() + h, z + 0.5, null);
                tnt.setFuse(StackedSettings.TNT_FUSE.asInt());
                level.addFreshEntity(tnt);
            });
        }

        for (int i = 0; i < StackedSettings.ANVIL_COUNT.asInt(); i++) {
            int x = origin.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int h = clearHeight(level, x, origin.getY(), z, height);
            if (h < 3) continue;
            Scheduler.later(RANDOM.nextInt(40), () -> {
                FallingBlockEntity anvil = FallingBlockEntity.fall(level, new BlockPos(x, origin.getY() + h, z),
                        Blocks.ANVIL.defaultBlockState());
                if (anvil != null) {
                    anvil.time = 1;
                    anvil.dropItem = true;
                    anvil.setHurtsEntities((float) StackedSettings.ANVIL_DAMAGE.asDouble(), 40);
                }
            });
        }
    }

    /** Highest free height (up to max) above this column, or 0 when it is blocked right away. */
    private static int clearHeight(ServerLevel level, int x, int baseY, int z, int max) {
        for (int h = max; h >= 3; h--) {
            if (Delivery.columnClear(level, x, baseY, z, h)) return h;
        }
        return 0;
    }
}
