package com.example.smartchat.eggs;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Eight food and gift easter eggs: hungry, snack, pizza, milk, feeling lucky, treasure, the cake, villagers friend. */
public final class GiftEggs {

    private static final Random R = new Random();

    private GiftEggs() {}

    public static void register() {
        hungry();
        snack();
        pizza();
        milk();
        lucky();
        treasure();
        cakeIsALie();
        villagersFriend();
    }

    private static void give(ChatContext ctx, String path, int count) {
        Tools.give(ctx.player, Tools.stack(path, count));
    }

    // ------------------------------------------------------------------ the eggs

    private static void hungry() {
        Egg.define("hungry", "I'm Hungry", Egg.Category.GIFTS, "A full food bar and a steak.",
                "im hungry", "i am hungry", "so hungry", "starving").run(ctx -> {
            ctx.player.getFoodData().setFoodLevel(20);
            Tools.effect(ctx.level, ctx.player, "saturation", 1, 5);
            give(ctx, "cooked_beef", 4);
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.PLAYER_BURP, 1.0f, 1.0f);
            ctx.reply("Dinner is served! Food bar full, plus a few steaks.");
        });
    }

    private static void snack() {
        String[][] snacks = {{"bread", "8"}, {"apple", "6"}, {"baked_potato", "6"}, {"cooked_chicken", "6"},
                {"cooked_porkchop", "6"}, {"cookie", "12"}, {"pumpkin_pie", "4"}, {"carrot", "8"},
                {"melon_slice", "12"}, {"sweet_berries", "10"}, {"golden_carrot", "4"}};
        Egg.define("snack", "Gimme a Snack", Egg.Category.GIFTS, "A random snack.",
                "gimme a snack", "give me a snack", "snack time", "i want a snack").run(ctx -> {
            String[] pick = snacks[R.nextInt(snacks.length)];
            give(ctx, pick[0], Integer.parseInt(pick[1]));
            ctx.reply("Snack time! You got " + pick[1] + "x " + Delivery.pretty(pick[0]) + ".");
        });
    }

    private static void pizza() {
        Egg.define("pizza", "Pizza", Egg.Category.GIFTS, "A fresh pie named Pizza.", "pizza", "pizza time")
                .exact().run(ctx -> {
            ItemStack pie = Tools.stack("pumpkin_pie", 3);
            if (pie != null) {
                pie.set(DataComponents.CUSTOM_NAME, Component.literal("Pizza").withStyle(ChatFormatting.GOLD));
                Tools.give(ctx.player, pie);
            }
            ctx.reply("One hot Pizza, coming up!");
        });
    }

    private static void milk() {
        Egg.define("milk", "Milk", Egg.Category.GIFTS, "A bucket of milk and a friendly moo.", "milk")
                .exact().run(ctx -> {
            give(ctx, "milk_bucket", 1);
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.COW_AMBIENT, 1.0f, 1.0f);
            ctx.reply("Got milk? Now you do.");
        });
    }

    private static void lucky() {
        Egg.define("lucky", "Feeling Lucky", Egg.Category.GIFTS, "A loot roll with a one percent jackpot.",
                "feeling lucky", "im feeling lucky", "feel lucky").run(ctx -> {
            ServerLevel level = ctx.level;
            int roll = R.nextInt(100);
            if (roll == 0) {
                give(ctx, "diamond", 16);
                give(ctx, "enchanted_golden_apple", 2);
                Tools.title(level, ctx.player, "JACKPOT!", "The 1% hit!");
                Tools.sound(level, ctx.player.blockPosition(), SoundEvents.PLAYER_LEVELUP, 1.5f, 1.0f);
                Tools.particles(level, ParticleTypes.HAPPY_VILLAGER, ctx.player.getX(), ctx.player.getY() + 1.5, ctx.player.getZ(), 40, 1.0, 0.1);
            } else if (roll < 10) {
                String[][] good = {{"emerald", "8"}, {"golden_apple", "2"}, {"diamond", "3"}, {"experience_bottle", "16"}};
                String[] pick = good[R.nextInt(good.length)];
                give(ctx, pick[0], Integer.parseInt(pick[1]));
                ctx.reply("Nice roll! You got " + pick[1] + "x " + Delivery.pretty(pick[0]) + ".");
            } else if (roll < 40) {
                String[][] ok = {{"iron_ingot", "8"}, {"gold_ingot", "5"}, {"copper_ingot", "12"}, {"lapis_lazuli", "10"}};
                String[] pick = ok[R.nextInt(ok.length)];
                give(ctx, pick[0], Integer.parseInt(pick[1]));
                ctx.reply("Not bad! You got " + pick[1] + "x " + Delivery.pretty(pick[0]) + ".");
            } else {
                String[][] junk = {{"stick", "3"}, {"string", "2"}, {"cobweb", "1"}, {"bowl", "1"}, {"dirt", "5"}, {"flint", "2"}};
                String[] pick = junk[R.nextInt(junk.length)];
                give(ctx, pick[0], Integer.parseInt(pick[1]));
                ctx.reply("Oof. You got " + pick[1] + "x " + Delivery.pretty(pick[0]) + ". Better luck next time!");
            }
        });
    }

    private static void treasure() {
        String[][] loot = {{"gold_ingot", "8"}, {"emerald", "4"}, {"diamond", "2"}, {"iron_ingot", "12"},
                {"golden_apple", "1"}, {"ender_pearl", "4"}, {"experience_bottle", "8"}, {"name_tag", "1"}};
        Egg.define("treasure", "Treasure", Egg.Category.GIFTS, "A chest is buried nearby, and sparkles mark the spot.",
                "treasure").exact().run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            if (!level.dimension().equals(Level.OVERWORLD)) {
                ctx.reply("Treasure can only be buried in the Overworld.");
                return;
            }
            BlockPos spot = null;
            int surfaceY = 0;
            for (int attempt = 0; attempt < 10 && spot == null; attempt++) {
                double angle = R.nextDouble() * Math.PI * 2;
                double dist = 10 + R.nextInt(11);
                int x = o.getX() + (int) Math.round(Math.cos(angle) * dist);
                int z = o.getZ() + (int) Math.round(Math.sin(angle) * dist);
                level.getChunk(x >> 4, z >> 4);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos chestPos = new BlockPos(x, top - 3, z);
                BlockState surface = level.getBlockState(new BlockPos(x, top - 1, z));
                BlockState inside = level.getBlockState(chestPos);
                if (surface.isAir() || !surface.getFluidState().isEmpty()) continue;
                if (inside.isAir() || !inside.getFluidState().isEmpty() || level.getBlockEntity(chestPos) != null) continue;
                spot = chestPos;
                surfaceY = top;
            }
            if (spot == null) {
                ctx.reply("I couldn't find good ground to bury treasure here. Try somewhere else!");
                return;
            }
            level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(spot);
            if (be instanceof Container chest) {
                int slot = 0;
                for (String[] entry : loot) {
                    ItemStack stack = Tools.stack(entry[0], Integer.parseInt(entry[1]));
                    if (stack != null && slot < chest.getContainerSize()) chest.setItem(slot++, stack);
                }
                chest.setChanged();
            }

            int dx = spot.getX() - o.getX();
            int dz = spot.getZ() - o.getZ();
            String ns = dz < 0 ? "north" : "south";
            String ew = dx > 0 ? "east" : "west";
            String dir = Math.abs(dx) < Math.abs(dz) / 2 ? ns : Math.abs(dz) < Math.abs(dx) / 2 ? ew : ns + "-" + ew;
            ctx.reply("Treasure is buried about " + (int) Math.sqrt(dx * dx + dz * dz) + " blocks to the " + dir
                    + ", a few blocks underground. Follow the sparkles and dig!");

            final BlockPos mark = spot;
            final int markY = surfaceY;
            Scheduler.repeat(0, 5, 60, i -> Tools.particles(level, ParticleTypes.HAPPY_VILLAGER,
                    mark.getX() + 0.5, markY + 1.0, mark.getZ() + 0.5, 8, 0.4, 0.05));
        });
    }

    private static void cakeIsALie() {
        Egg.define("cakelie", "The Cake Is a Lie", Egg.Category.GIFTS, "You get a cake... for ten seconds.",
                "the cake is a lie", "cake is a lie").run(ctx -> {
            ServerPlayer p = ctx.player;
            give(ctx, "cake", 1);
            ctx.reply("Here's your cake. Enjoy it!");
            Scheduler.later(200, () -> {
                Inventory inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("cake")) {
                        s.shrink(1);
                        break;
                    }
                }
                ctx.reply("...The cake was a lie.");
                Tools.sound(ctx.level, p.blockPosition(), SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
            });
        });
    }

    private static void villagersFriend() {
        Egg.define("villagersfriend", "Villager's Friend", Egg.Category.GIFTS, "Emeralds and the villagers' gratitude.",
                "villagers friend", "villager friend").run(ctx -> {
            give(ctx, "emerald", 8);
            Tools.effect(ctx.level, ctx.player, "hero_of_the_village", 60, 0);
            Tools.title(ctx.level, ctx.player, "Friend of the village!", "Hero of the Village for a minute");
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
        });
    }
}
