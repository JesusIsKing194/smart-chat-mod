package com.example.smartchat.eggs;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Settings;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thirteen fun eggs: bees, warden, megaphone, rubber duck, hello there, coin and dice, sing, sus, F, gg, trap, noclip, troll. */
public final class FunEggs {

    private static final Random R = new Random();
    private static final Pattern DICE = Pattern.compile("(\\d*)d(\\d+)");

    private FunEggs() {}

    public static void register() {
        bees();
        warden();
        megaphone();
        rubberDuck();
        helloThere();
        coinAndDice();
        sing();
        sus();
        pressF();
        gg();
        itsATrap();
        noclip();
        opTroll();
    }

    // ------------------------------------------------------------------ creatures

    private static void bees() {
        Egg.define("bees", "Bees", Egg.Category.CREATURES, "A buzzing swarm circles you, and you get honey.",
                "bees", "bee swarm").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            Tools.give(p, Tools.stack("honey_bottle", 1));
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.BEE_LOOP, 1.5f, 1.0f);
            Scheduler.repeat(0, 2, 150, step -> {
                for (int k = 0; k < 6; k++) {
                    double a = step * 0.35 + k * Math.PI / 3;
                    double r = 1.5 + Math.sin(step * 0.1 + k) * 0.6;
                    Tools.particles(ctx.level, ParticleTypes.DRIPPING_HONEY, p.getX() + Math.cos(a) * r,
                            p.getY() + 1.0 + Math.sin(a * 2) * 0.5, p.getZ() + Math.sin(a) * r, 1, 0.05, 0.0);
                }
                if (step % 20 == 0) Tools.sound(ctx.level, p.blockPosition(), SoundEvents.BEE_POLLINATE, 1.0f, 1.0f);
            });
            ctx.reply("Bzzzz! You've been chosen by the bees. Have some honey.");
        });
    }

    private static void warden() {
        Egg.define("warden", "Warden", Egg.Category.CREATURES, "A heartbeat and creeping darkness. No real Warden.",
                "warden").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            Scheduler.repeat(0, 24, 6, i -> {
                Tools.sound(ctx.level, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, 2.0f, 1.0f);
                Tools.effect(ctx.level, p, "darkness", 3, 0);
            });
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSE, 1.5f, 1.0f);
            Tools.title(ctx.level, p, "...", "Something is listening");
        });
    }

    // ------------------------------------------------------------------ assistant and chat

    private static void megaphone() {
        Egg.define("megaphone", "Megaphone", Egg.Category.PLAYER, "Your message shows as a big title for everyone nearby.",
                "megaphone").run(ctx -> {
            String raw = ctx.raw;
            int at = raw.toLowerCase(Locale.ROOT).indexOf("megaphone");
            String text = at < 0 ? "" : raw.substring(at + "megaphone".length()).replaceAll("^[\\s:,-]+", "").trim();
            if (text.isEmpty()) {
                ctx.reply("Say what you want to shout: 'megaphone hello everyone'.");
                return;
            }
            if (text.length() > 60) text = text.substring(0, 60);
            String name = ctx.player.getName().getString();
            for (ServerPlayer p : Tools.nearby(ctx, 30)) Tools.title(ctx.level, p, text, name + " shouts");
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 0.5f);
        });
    }

    private static void rubberDuck() {
        Egg.define("rubberduck", "Rubber Duck", Egg.Category.ASSISTANT, "A rubber duck to explain your problems to.",
                "rubber duck").exact().run(ctx -> {
            ItemStack duck = Tools.stack("yellow_dye", 1);
            if (duck != null) {
                duck.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("Rubber Duck").withStyle(net.minecraft.ChatFormatting.YELLOW));
                Tools.give(ctx.player, duck);
            }
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.CHICKEN_AMBIENT, 1.0f, 2.0f);
            ctx.reply("Quack! Explain your problem to the duck. Saying it out loud usually solves it.");
        });
    }

    private static void helloThere() {
        Egg.define("hellothere", "Hello There", Egg.Category.ASSISTANT, "The assistant has a famous reply.",
                "hello there").exact().run(ctx -> {
            ctx.reply("General Kenobi!");
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
        });
    }

    private static void coinAndDice() {
        Egg.define("coindice", "Coin and Dice", Egg.Category.ASSISTANT, "Flip a coin or roll any dice, like 2d6 or d20.",
                "flip a coin", "coin flip", "flip coin", "roll a die", "roll a dice", "roll the dice", "roll dice",
                "roll d20", "roll d6").run(ctx -> {
            ServerPlayer p = ctx.player;
            if (ctx.has("coin")) {
                boolean heads = R.nextBoolean();
                Tools.particles(ctx.level, ParticleTypes.CRIT, p.getX(), p.getY() + 2, p.getZ(), 15, 0.3, 0.1);
                Tools.sound(ctx.level, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.5f);
                ctx.reply("The coin spins... " + (heads ? "HEADS!" : "TAILS!"));
                return;
            }
            int count = 1;
            int sides = 6;
            for (String token : ctx.tokens) {
                Matcher m = DICE.matcher(token);
                if (m.matches()) {
                    if (!m.group(1).isEmpty()) count = Integer.parseInt(m.group(1));
                    sides = Integer.parseInt(m.group(2));
                }
            }
            count = Math.max(1, Math.min(count, 10));
            sides = Math.max(2, Math.min(sides, 1000));
            StringBuilder rolls = new StringBuilder();
            int total = 0;
            int last = 0;
            for (int i = 0; i < count; i++) {
                last = 1 + R.nextInt(sides);
                total += last;
                if (i > 0) rolls.append(", ");
                rolls.append(last);
            }
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, 1.0f);
            String result = "Rolling " + count + "d" + sides + ": " + rolls + (count > 1 ? " (total " + total + ")" : "");
            if (count == 1 && sides == 20 && last == 20) {
                result += "  CRITICAL HIT!";
                Tools.particles(ctx.level, ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 40, 0.8, 0.15);
            } else if (count == 1 && sides == 20 && last == 1) {
                result += "  Critical fail...";
            }
            ctx.reply(result);
        });
    }

    private static void sing() {
        Egg.define("sing", "Sing", Egg.Category.ASSISTANT, "The assistant sings a little song.",
                "sing", "sing a song", "sing me a song").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            int[] tune = {12, 14, 16, 12, 12, 14, 16, 12, 16, 17, 19, 16, 17, 19, 19, 21, 19, 17, 16, 12, 19, 21, 19, 17, 16, 12, 12, 7, 12};
            Scheduler.repeat(0, 4, tune.length, i -> Tools.sound(ctx.level, p.blockPosition(),
                    SoundEvents.NOTE_BLOCK_HARP.value(), 1.5f, (float) Math.pow(2, (tune[i] - 12) / 12.0)));
            ctx.reply("La la la, digging down so deep...");
            Scheduler.later(40, () -> ctx.reply("Torches in my pocket, creepers fast asleep..."));
            Scheduler.later(80, () -> ctx.reply("La la la, mining all day long!"));
        });
    }

    private static void sus() {
        Egg.define("sus", "Sus", Egg.Category.ASSISTANT, "Very suspicious.", "sus").exact().run(ctx -> {
            Tools.title(ctx.level, ctx.player, "SUS", "Very suspicious...");
            Tools.particles(ctx.level, ParticleTypes.ANGRY_VILLAGER, ctx.player.getX(), ctx.player.getY() + 2, ctx.player.getZ(), 8, 0.6, 0.02);
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.VILLAGER_NO, 1.0f, 0.7f);
        });
    }

    private static void pressF() {
        Egg.define("pressf", "Press F", Egg.Category.ASSISTANT, "Everyone pays their respects.",
                "press f", "f in chat", "f in the chat", "pay respects").run(ctx -> {
            String name = ctx.player.getName().getString();
            for (ServerPlayer p : ctx.level.getServer().getPlayerList().getPlayers()) {
                Messages.say(p, "F");
                Messages.say(p, "Respects have been paid to " + name + ".");
            }
        });
    }

    private static void gg() {
        Egg.define("gg", "GG", Egg.Category.ASSISTANT, "Fireworks and a friendly gg.", "gg", "gg wp", "good game")
                .exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            Tools.particles(ctx.level, ParticleTypes.FIREWORK, p.getX(), p.getY() + 2, p.getZ(), 50, 1.0, 0.2);
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
            ctx.reply("gg!");
        });
    }

    private static void itsATrap() {
        Egg.define("itsatrap", "It's a Trap", Egg.Category.ASSISTANT, "A harmless fake trap.", "its a trap")
                .run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos pos = ctx.player.blockPosition();
            Tools.sound(level, pos, SoundEvents.TRIPWIRE_CLICK_ON, 1.5f, 1.0f);
            Scheduler.repeat(10, 3, 6, i -> {
                Tools.sound(level, pos, SoundEvents.DISPENSER_DISPENSE, 1.0f, 1.0f);
                Tools.sound(level, pos, SoundEvents.ARROW_SHOOT, 1.0f, 0.9f + R.nextFloat() * 0.3f);
            });
            Scheduler.later(45, () -> Tools.title(level, ctx.player, "Just kidding!", "No arrows were harmed"));
        });
    }

    private static void noclip() {
        Egg.define("noclip", "Noclip", Egg.Category.ASSISTANT, "Nice try.", "noclip").exact().run(ctx -> {
            Tools.title(ctx.level, ctx.player, "Nice try!", "That's cheating");
            Tools.particles(ctx.level, ParticleTypes.SMOKE, ctx.player.getX(), ctx.player.getY() + 1, ctx.player.getZ(), 20, 0.5, 0.02);
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        });
    }

    private static void opTroll() {
        Egg.define("optroll", "Nice Try", Egg.Category.ASSISTANT, "Asking for operator powers does not end well.",
                "op me", "give me op", "make me op", "make me admin", "give me admin", "creative mode", "gamemode creative")
                .run(ctx -> {
            ServerPlayer p = ctx.player;
            if (Settings.ADMIN_NAMES.asList().contains(p.getName().getString().toLowerCase(Locale.ROOT))) {
                ctx.reply("You're already an admin, no tricks needed.");
                return;
            }
            ServerLevel level = ctx.level;
            BlockPos pos = p.blockPosition();
            Tools.sound(level, pos, SoundEvents.VILLAGER_HURT, 1.0f, 0.5f);
            Tools.title(level, p, "Nice try!", "Here's a little something");
            PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY() + 6.0, pos.getZ() + 0.5, null);
            tnt.setFuse(40);
            level.addFreshEntity(tnt);
        });
    }
}
