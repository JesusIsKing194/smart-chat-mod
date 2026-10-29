package com.example.smartchat.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Hands items to a player: dropped in the 3x3 area around them, or straight into the inventory
 * when something (like a cave ceiling) would stop the drop from reaching them.
 */
public final class Delivery {

    public record Result(int dropped, int toInventory) {}

    private static final Random RANDOM = new Random();

    private Delivery() {}

    /** True when every block from 1 to height above baseY in this column lets things fall through. */
    public static boolean columnClear(ServerLevel level, int x, int baseY, int z, int height) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= height; dy++) {
            pos.set(x, baseY + dy, z);
            if (!level.hasChunkAt(pos)) return false;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }

    public static boolean isInteractableBlock(Block block) {
        if (block instanceof BaseEntityBlock) return true;
        return block instanceof AnvilBlock || block instanceof CraftingTableBlock || block instanceof EnderChestBlock
                || block instanceof EnchantingTableBlock || block instanceof LoomBlock
                || block instanceof CartographyTableBlock || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock || block instanceof SmithingTableBlock;
    }

    public static Result deliver(ServerPlayer player, List<ItemStack> stacks) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos base = player.blockPosition();
        int radius = ItemSettings.DROP_RADIUS.asInt();
        int height = ItemSettings.DROP_HEIGHT.asInt();
        int blockHeight = ItemSettings.BLOCK_DROP_HEIGHT.asInt();
        boolean fallback = ItemSettings.FALLBACK_TO_INVENTORY.asBoolean();

        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) cells.add(new int[]{dx, dz});
        }

        int dropped = 0;
        int inventory = 0;
        for (ItemStack stack : stacks) {
            BlockItem blockItem = stack.getItem() instanceof BlockItem b ? b : null;
            boolean usable = ItemSettings.PLACE_USABLE_BLOCKS.asBoolean() && blockItem != null
                    && isInteractableBlock(blockItem.getBlock());
            int h = usable ? blockHeight : height;

            Collections.shuffle(cells, RANDOM);
            int[] cell = null;
            for (int[] c : cells) {
                if (columnClear(level, base.getX() + c[0], base.getY(), base.getZ() + c[1], h)) {
                    cell = c;
                    break;
                }
            }
            if (cell == null) {
                if (fallback) {
                    toInventory(player, stack.copy());
                    inventory += stack.getCount();
                }
                continue;
            }

            int x = base.getX() + cell[0];
            int z = base.getZ() + cell[1];
            if (usable) {
                int falling = Math.min(stack.getCount(), 16);
                for (int i = 0; i < falling; i++) {
                    int rise = h + i * 2;
                    if (!columnClear(level, x, base.getY(), z, rise)) {
                        if (fallback) toInventory(player, new ItemStack(stack.getItem(), 1));
                        inventory++;
                        continue;
                    }
                    FallingBlockEntity entity = FallingBlockEntity.fall(level, new BlockPos(x, base.getY() + rise, z),
                            blockItem.getBlock().defaultBlockState());
                    if (entity != null) {
                        entity.time = 1;
                        entity.dropItem = true;
                    }
                    dropped++;
                }
                int extra = stack.getCount() - falling;
                if (extra > 0) {
                    toInventory(player, new ItemStack(stack.getItem(), extra));
                    inventory += extra;
                }
            } else {
                double ox = (RANDOM.nextDouble() - 0.5) * 0.4;
                double oz = (RANDOM.nextDouble() - 0.5) * 0.4;
                ItemEntity entity = new ItemEntity(level, x + 0.5 + ox, base.getY() + h, z + 0.5 + oz, stack.copy());
                level.addFreshEntity(entity);
                dropped += stack.getCount();
            }
        }

        if (!stacks.isEmpty()) effects(player, level, BuiltInRegistries.ITEM.getKey(stacks.get(0).getItem()).getPath());
        return new Result(dropped, inventory);
    }

    /** Puts the stack into the inventory; whatever does not fit is dropped at the player's feet. */
    public static void toInventory(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty()) {
            ServerLevel level = (ServerLevel) player.level();
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), copy));
        }
    }

    private static void effects(ServerPlayer player, ServerLevel level, String path) {
        if (!ItemSettings.EFFECTS.asBoolean()) return;
        BlockPos pos = player.blockPosition().above(3);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
        if (path.contains("diamond")) {
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX(), pos.getY(), pos.getZ(), 25, 0.5, 0.5, 0.5, 0.1);
        } else if (path.contains("gold")) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (path.contains("emerald")) {
            level.sendParticles(ParticleTypes.COMPOSTER, pos.getX(), pos.getY(), pos.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        } else if (path.contains("netherite")) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.05);
        } else {
            level.sendParticles(ParticleTypes.PORTAL, pos.getX(), pos.getY(), pos.getZ(), 15, 0.5, 0.5, 0.5, 0.2);
        }
    }

    /** "netherite_ingot" or "netherite ingot" -> "Netherite Ingot". */
    public static String pretty(String key) {
        String[] words = key.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
