package com.example.smartchat.eggs;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.stacked.LootBoxes;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seven celebration eggs: fireworks, party, birthday, christmas, halloween, gold rush and bubble bath. */
public final class PartyEggs {

    private static final Random R = new Random();

    private PartyEggs() {}

    public static void register() {
        fireworks();
        party();
        birthday();
        christmas();
        halloween();
        goldRush();
        bubbleBath();
    }

    // ------------------------------------------------------------------ helpers

    /** A firework made of particles and sounds: it launches, then bursts 12 ticks later. */
    private static void burst(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        Tools.sound(level, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        Scheduler.later(12, () -> {
            Tools.particles(level, ParticleTypes.FIREWORK, x, y, z, 60, 1.5, 0.2);
            Tools.particles(level, ParticleTypes.END_ROD, x, y, z, 20, 1.5, 0.15);
            Tools.sound(level, pos, SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
            Tools.sound(level, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, 2.0f, 1.0f);
        });
    }

    private static void burstsAround(ChatContext ctx, int count, int spacingTicks) {
        ServerLevel level = ctx.level;
        BlockPos o = ctx.player.blockPosition();
        for (int i = 0; i < count; i++) {
            Scheduler.later(i * spacingTicks, () -> burst(level,
                    o.getX() + 0.5 + R.nextInt(21) - 10, o.getY() + 10 + R.nextInt(6), o.getZ() + 0.5 + R.nextInt(21) - 10));
        }
    }

    // ------------------------------------------------------------------ the eggs

    private static void fireworks() {
        Egg.define("fireworks", "Fireworks", Egg.Category.WORLD, "A firework show above you.",
                "fireworks", "firework show", "fireworks show").exact().run(ctx -> burstsAround(ctx, 6, 10));
    }

    private static void party() {
        Egg.define("party", "Party", Egg.Category.WORLD, "Confetti, fireworks and a little tune.",
                "party", "party time", "lets party").exact().run(ctx -> {
            ServerLevel level = ctx.level;
            List<ServerPlayer> group = Tools.nearby(ctx, 12);
            Tools.title(level, ctx.player, "Party time!", "");
            burstsAround(ctx, 4, 25);
            // confetti: note particles take their colour from the "speed" slot
            Scheduler.repeat(0, 4, 50, step -> {
                for (ServerPlayer p : group) {
                    for (int k = 0; k < 12; k++) {
                        level.sendParticles(ParticleTypes.NOTE, p.getX() + R.nextDouble() * 6 - 3, p.getY() + 3 + R.nextDouble() * 2,
                                p.getZ() + R.nextDouble() * 6 - 3, 0, R.nextDouble(), 0.0, 0.0, 1.0);
                    }
                }
            });
            // a short tune on the bell
            int[] tune = {0, 2, 4, 5, 7, 9, 11, 12, 11, 9, 7, 5, 4, 2, 0};
            Scheduler.repeat(0, 5, tune.length, i -> Tools.sound(level, ctx.player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, (float) (0.5 * Math.pow(2, tune[i] / 12.0))));
        });
    }

    private static void birthday() {
        Egg.define("birthday", "Happy Birthday", Egg.Category.WORLD, "A cake, fireworks and birthday wishes.",
                "happy birthday", "its my birthday", "my birthday").run(ctx -> {
            ServerLevel level = ctx.level;
            String name = ctx.player.getName().getString();
            Tools.give(ctx.player, Tools.stack("cake", 1));
            Tools.title(level, ctx.player, "Happy Birthday!", name);
            burstsAround(ctx, 5, 12);
            Scheduler.repeat(0, 6, 8, i -> Tools.sound(level, ctx.player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.8f + i * 0.1f));
            for (ServerPlayer p : Tools.nearby(ctx, 16)) {
                if (p != ctx.player) Messages.say(p, "Happy birthday, " + name + "!");
            }
        });
    }

    private static void christmas() {
        Egg.define("christmas", "Merry Christmas", Egg.Category.WORLD, "Snow, bells and a shulker box of treats.",
                "merry christmas", "happy holidays", "merry xmas").run(ctx -> {
            ServerLevel level = ctx.level;
            ServerPlayer player = ctx.player;
            List<ItemStack> gifts = new ArrayList<>();
            String[][] treats = {{"cookie", "16"}, {"cake", "1"}, {"golden_apple", "2"}, {"sweet_berries", "8"},
                    {"pumpkin_pie", "8"}, {"snowball", "16"}, {"golden_carrot", "8"}};
            for (String[] t : treats) {
                ItemStack s = Tools.stack(t[0], Integer.parseInt(t[1]));
                if (s != null) gifts.add(s);
            }
            Delivery.toInventory(player, LootBoxes.asItem(new LootBoxes.Box("red_shulker_box", gifts)));
            Tools.title(level, player, "Merry Christmas!", "A gift is in your inventory");
            Scheduler.repeat(0, 4, 60, step -> {
                Tools.particles(level, ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 6, player.getZ(), 25, 4.0, 0.02);
                if (step % 10 == 0) Tools.sound(level, player.blockPosition(), SoundEvents.BELL_BLOCK, 1.0f, 1.0f + (step % 20) * 0.02f);
            });
        });
    }

    private static void halloween() {
        Egg.define("halloween", "Happy Halloween", Egg.Category.WORLD, "A pumpkin, candy and spooky sounds.",
                "happy halloween", "trick or treat").run(ctx -> {
            ServerLevel level = ctx.level;
            ServerPlayer player = ctx.player;
            Tools.give(player, Tools.stack("carved_pumpkin", 1));
            Tools.give(player, Tools.stack("pumpkin_pie", 4));
            Tools.give(player, Tools.stack("cookie", 8));
            Tools.title(level, player, "Trick or treat!", "");
            Tools.sound(level, player.blockPosition(), SoundEvents.WITCH_AMBIENT, 1.5f, 0.8f);
            Scheduler.later(30, () -> Tools.sound(level, player.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, 1.0f, 0.6f));
            Scheduler.repeat(0, 4, 40, step -> {
                Tools.particles(level, ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 6, 1.5, 0.02);
                Tools.particles(level, ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 4, 1.5, 0.02);
            });
        });
    }

    private static void goldRush() {
        Egg.define("goldrush", "Gold Rush", Egg.Category.GIFTS, "Gold nuggets rain down in a 12x12 area.",
                "gold rush").run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            Tools.title(level, ctx.player, "Gold rush!", "");
            Scheduler.repeat(0, 2, 40, step -> {
                for (int n = 0; n < 2; n++) {
                    int x = o.getX() + R.nextInt(13) - 6;
                    int z = o.getZ() + R.nextInt(13) - 6;
                    if (!Delivery.columnClear(level, x, o.getY(), z, 10)) continue; // blocked by a ceiling: skip
                    ItemStack nugget = Tools.stack("gold_nugget", 1);
                    if (nugget != null) level.addFreshEntity(new ItemEntity(level, x + 0.5, o.getY() + 10, z + 0.5, nugget));
                }
                if (step % 5 == 0) Tools.sound(level, o, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.2f + R.nextFloat() * 0.6f);
            });
        });
    }

    private static void bubbleBath() {
        Egg.define("bubblebath", "Bubble Bath", Egg.Category.WORLD, "Bubbles all around and a little breathing help.",
                "bubble bath").run(ctx -> {
            ServerLevel level = ctx.level;
            ServerPlayer p = ctx.player;
            Tools.effect(level, p, "water_breathing", 20, 0);
            Tools.sound(level, p.blockPosition(), SoundEvents.PLAYER_SPLASH, 1.0f, 1.0f);
            Scheduler.repeat(0, 4, 50, step -> {
                Tools.particles(level, ParticleTypes.BUBBLE, p.getX(), p.getY() + 1, p.getZ(), 20, 1.0, 0.05);
                Tools.particles(level, ParticleTypes.BUBBLE_POP, p.getX(), p.getY() + 1.5, p.getZ(), 6, 1.0, 0.02);
                Tools.particles(level, ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.5, p.getZ(), 2, 0.8, 0.02);
            });
        });
    }
}
