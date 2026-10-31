package com.example.smartchat.eggs;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Sixteen easter eggs that change the player: flight, size, effects, spinning, magnets and more. */
public final class PlayerEggs {

    private static final Random R = new Random();

    private PlayerEggs() {}

    public static void register() {
        fly();
        moonGravity();
        tiny();
        giant();
        effectEgg("invisible", "Invisible", "Invisibility for a minute.", "invisible", "make me invisible");
        effectEgg("glowup", "Glow Up", "A glowing outline for a minute.", "glow up");
        ninja();
        effectEgg("superjump", "Super Jump", "A huge jump boost for 30 seconds.", "super jump");
        underwater();
        effectEgg("lavaswim", "Lava Swim", "Fire resistance for two minutes.", "lava swim");
        warmHug();
        barrelRoll();
        ghost();
        balloon();
        abracadabra();
        magnet();
    }

    // ------------------------------------------------------------------ helpers

    private static void run(ChatContext ctx, String command) {
        Tools.command(ctx.level, command);
    }

    private static String as(ServerPlayer p) {
        return "execute as " + p.getUUID() + " at @s run ";
    }

    private static boolean hasRoom(ChatContext ctx, int height) {
        BlockPos f = ctx.player.blockPosition();
        return Delivery.columnClear(ctx.level, f.getX(), f.getY(), f.getZ(), height);
    }

    /** The simple "name your effect" eggs: invisible, glow up, super jump, lava swim. */
    private static void effectEgg(String id, String name, String description, String... triggers) {
        Egg.define(id, name, Egg.Category.PLAYER, description, triggers).exact().run(ctx -> {
            ServerLevel level = ctx.level;
            switch (id) {
                case "invisible":
                    Tools.effect(level, ctx.player, "invisibility", 60, 0);
                    Tools.title(level, ctx.player, "Now you see me...", "or not");
                    break;
                case "glowup":
                    Tools.effect(level, ctx.player, "glowing", 60, 0);
                    Tools.title(level, ctx.player, "Glow up!", "");
                    break;
                case "superjump":
                    Tools.effect(level, ctx.player, "jump_boost", 30, 4);
                    Tools.title(level, ctx.player, "Super jump!", "");
                    break;
                default:
                    Tools.effect(level, ctx.player, "fire_resistance", 120, 0);
                    Tools.title(level, ctx.player, "Lava swim!", "Fire can't hurt you");
                    break;
            }
            Tools.sound(level, ctx.player.blockPosition(), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
        });
    }

    // ------------------------------------------------------------------ the eggs

    private static void fly() {
        Egg.define("fly", "I Believe I Can Fly", Egg.Category.PLAYER, "30 seconds of flight, then a soft landing.",
                "i believe i can fly").run(ctx -> {
            ServerPlayer p = ctx.player;
            if (p.isCreative() || p.isSpectator() || p.getAbilities().mayfly) {
                ctx.reply("You can already fly!");
                return;
            }
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            Tools.title(ctx.level, p, "Believe!", "30 seconds of flight");
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.5f);
            Scheduler.later(600, () -> {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                Tools.effect(ctx.level, p, "slow_falling", 10, 0);
            });
        });
    }

    private static void moonGravity() {
        Egg.define("moongravity", "Moon Gravity", Egg.Category.PLAYER, "Low gravity for a minute.", "moon gravity")
                .run(ctx -> {
            Tools.effect(ctx.level, ctx.player, "jump_boost", 60, 2);
            Tools.effect(ctx.level, ctx.player, "slow_falling", 60, 0);
            Tools.title(ctx.level, ctx.player, "Moon gravity!", "");
        });
    }

    private static void tiny() {
        Egg.define("tiny", "Tiny", Egg.Category.PLAYER, "Shrinks you for a minute.",
                "tiny", "shrink me", "make me tiny", "normal size", "reset my size").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            if (ctx.has("normal size") || ctx.has("reset my size")) {
                run(ctx, "attribute " + p.getUUID() + " minecraft:scale base set 1");
                ctx.reply("Back to normal size.");
                return;
            }
            run(ctx, "attribute " + p.getUUID() + " minecraft:scale base set 0.5");
            Tools.title(ctx.level, p, "Tiny!", "Say 'normal size' to undo");
            Scheduler.later(1200, () -> run(ctx, "attribute " + p.getUUID() + " minecraft:scale base set 1"));
        });
    }

    private static void giant() {
        Egg.define("giant", "Giant", Egg.Category.PLAYER, "Grows you for a minute.", "giant", "grow me", "make me giant")
                .exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            if (!hasRoom(ctx, 4)) {
                ctx.reply("There isn't enough room to grow here!");
                return;
            }
            run(ctx, "attribute " + p.getUUID() + " minecraft:scale base set 2");
            Tools.title(ctx.level, p, "Giant!", "Say 'normal size' to undo");
            Scheduler.later(1200, () -> run(ctx, "attribute " + p.getUUID() + " minecraft:scale base set 1"));
        });
    }

    private static void ninja() {
        Egg.define("ninja", "Ninja", Egg.Category.PLAYER, "Speed and a short vanishing act.", "ninja").exact().run(ctx -> {
            Tools.effect(ctx.level, ctx.player, "speed", 20, 1);
            Tools.effect(ctx.level, ctx.player, "invisibility", 10, 0);
            Tools.title(ctx.level, ctx.player, "Ninja!", "");
            Tools.particles(ctx.level, ParticleTypes.POOF, ctx.player.getX(), ctx.player.getY() + 1, ctx.player.getZ(), 30, 0.5, 0.05);
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.5f);
        });
    }

    private static void underwater() {
        Egg.define("underwater", "Underwater", Egg.Category.PLAYER, "Breathe and swim like a dolphin for two minutes.",
                "underwater").exact().run(ctx -> {
            Tools.effect(ctx.level, ctx.player, "water_breathing", 120, 0);
            Tools.effect(ctx.level, ctx.player, "dolphins_grace", 120, 0);
            Tools.title(ctx.level, ctx.player, "Underwater!", "");
        });
    }

    private static void warmHug() {
        Egg.define("warmhug", "Warm Hug", Egg.Category.PLAYER, "Healing and a shower of hearts.", "warm hug").run(ctx -> {
            ServerPlayer p = ctx.player;
            Tools.effect(ctx.level, p, "regeneration", 10, 1);
            Tools.particles(ctx.level, ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 15, 0.6, 0.02);
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
            ctx.reply("Here's a big, warm hug!");
        });
    }

    private static void barrelRoll() {
        Egg.define("barrelroll", "Do a Barrel Roll", Egg.Category.PLAYER, "You spin all the way around.",
                "do a barrel roll", "barrel roll").run(ctx -> {
            ServerPlayer p = ctx.player;
            Tools.sound(ctx.level, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 1.2f);
            Scheduler.repeat(0, 1, 20, i -> run(ctx, as(p) + "tp @s ~ ~ ~ ~18 ~"));
        });
    }

    private static void ghost() {
        Egg.define("ghost", "Ghost", Egg.Category.PLAYER, "Invisible, glowing, and floating for 30 seconds.",
                "ghost", "become a ghost").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            ServerLevel level = ctx.level;
            int rise = 0;
            for (int h = 5; h >= 1; h--) {
                if (hasRoom(ctx, h + 2)) {
                    rise = h;
                    break;
                }
            }
            if (rise == 0) {
                ctx.reply("There isn't enough room to float up here!");
                return;
            }
            Tools.effect(level, p, "invisibility", 30, 0);
            Tools.effect(level, p, "glowing", 30, 0);
            Tools.effect(level, p, "slow_falling", 30, 0);
            Tools.title(level, p, "Boo!", "");
            Tools.sound(level, p.blockPosition(), SoundEvents.WITCH_AMBIENT, 1.0f, 0.6f);

            boolean hadFlight = p.getAbilities().mayfly;
            boolean canHold = !p.isCreative() && !p.isSpectator() && !hadFlight;
            if (canHold) {
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            }
            // float up about 5 blocks, then hover (flight keeps you in the air), then drift down with slow falling
            int steps = rise * 4;
            Scheduler.repeat(0, 1, steps, i -> run(ctx, as(p) + "tp @s ~ ~0.25 ~"));
            if (canHold) {
                Scheduler.later(480, () -> {
                    p.getAbilities().mayfly = false;
                    p.getAbilities().flying = false;
                    p.onUpdateAbilities();
                });
            }
            Scheduler.repeat(0, 5, 100, i -> Tools.particles(level, ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 3, 0.4, 0.01));
        });
    }

    private static void balloon() {
        Egg.define("balloon", "Balloon", Egg.Category.PLAYER, "Float up gently, then drift back down.", "balloon")
                .exact().run(ctx -> {
            if (!hasRoom(ctx, 8)) {
                ctx.reply("There isn't enough room to float up here!");
                return;
            }
            Tools.effect(ctx.level, ctx.player, "levitation", 4, 0);
            Tools.effect(ctx.level, ctx.player, "slow_falling", 20, 0);
            Tools.title(ctx.level, ctx.player, "Up, up and away!", "");
            Tools.sound(ctx.level, ctx.player.blockPosition(), SoundEvents.PLAYER_SPLASH, 0.6f, 1.8f);
        });
    }

    private static void abracadabra() {
        String[][] magic = {{"speed", "Speed"}, {"haste", "Haste"}, {"jump_boost", "Jump Boost"},
                {"regeneration", "Regeneration"}, {"night_vision", "Night Vision"}, {"glowing", "Glowing"},
                {"slow_falling", "Slow Falling"}, {"luck", "Luck"}, {"saturation", "Full Belly"}};
        Egg.define("abracadabra", "Abracadabra", Egg.Category.PLAYER, "A magic show and a random harmless effect.",
                "abracadabra").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            Scheduler.repeat(0, 3, 24, i -> {
                Tools.particles(ctx.level, ParticleTypes.WITCH, p.getX(), p.getY() + 1.5, p.getZ(), 12, 1.0, 0.05);
                Tools.particles(ctx.level, ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.5, p.getZ(), 12, 1.0, 0.3);
                Tools.particles(ctx.level, ParticleTypes.END_ROD, p.getX(), p.getY() + 2.0, p.getZ(), 4, 0.6, 0.05);
            });
            Scheduler.later(72, () -> {
                String[] pick = magic[R.nextInt(magic.length)];
                Tools.effect(ctx.level, p, pick[0], 30, 0);
                Tools.title(ctx.level, p, "Abracadabra!", "You got: " + pick[1]);
                Tools.sound(ctx.level, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.8f);
            });
        });
    }

    private static void magnet() {
        Egg.define("magnet", "Magnet", Egg.Category.PLAYER, "Pulls nearby dropped items to you for 30 seconds.",
                "magnet", "item magnet").exact().run(ctx -> {
            ServerPlayer p = ctx.player;
            Tools.title(ctx.level, p, "Magnet on!", "");
            Scheduler.repeat(0, 2, 300, i -> {
                Vec3 target = new Vec3(p.getX(), p.getY() + 0.5, p.getZ());
                for (ItemEntity item : ctx.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8.0))) {
                    Vec3 pull = target.subtract(item.getX(), item.getY(), item.getZ());
                    if (pull.length() > 0.3) item.setDeltaMovement(pull.normalize().scale(0.4));
                }
                if (i % 5 == 0) Tools.particles(ctx.level, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 6, 0.8, 0.05);
            });
        });
    }
}
