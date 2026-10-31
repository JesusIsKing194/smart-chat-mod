package com.example.smartchat.eggs;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.teleport.SafeSpot;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Twelve easter eggs that change the world around the player. */
public final class WorldEggs {

    public static final Setting SHAKE_BLOCKS = Settings.integer("eggs", "ground_shake_blocks", 5, 0, 30,
            "How many soft blocks nearby may break during the ground-shaking egg (0 = none).");

    public static final Setting SHAKE_BREAKABLE = Settings.list("eggs", "ground_shake_breakable",
            "dirt,grass_block,coarse_dirt,podzol,mud,gravel,sand,red_sand,clay,stone,cobblestone,andesite,diorite,"
                    + "granite,netherrack,sandstone,snow_block,dirt_path,deepslate",
            "Blocks the ground-shaking egg is allowed to break.");

    private static final Random R = new Random();

    private WorldEggs() {}

    public static void register() {
        light();
        disco();
        shake();
        meteor();
        aurora();
        snowDay();
        zeus();
        tornado();
        rainbow();
        flowerRain();
        shootingStar();
        beacon();
    }

    // ------------------------------------------------------------------ helpers

    private static BlockPos standSpot(ServerLevel level, int x, int y, int z) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (!level.hasChunkAt(pos)) return null;
            BlockPos below = pos.below();
            if (level.getBlockState(pos).isAir() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ the eggs

    private static void light() {
        Egg.define("light", "Let There Be Light", Egg.Category.WORLD, "Lights up the dark around you for a minute.",
                "let there be light").run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            List<BlockPos> spots = new ArrayList<>();
            for (int dx = -12; dx <= 12; dx += 4) {
                for (int dz = -12; dz <= 12; dz += 4) {
                    BlockPos s = standSpot(level, o.getX() + dx, o.getY(), o.getZ() + dz);
                    if (s == null) continue;
                    Tools.command(level, "setblock " + s.getX() + " " + s.getY() + " " + s.getZ() + " minecraft:light[level=15] keep");
                    Tools.particles(level, ParticleTypes.END_ROD, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 8, 0.3, 0.05);
                    spots.add(s);
                }
            }
            Tools.title(level, ctx.player, "Let there be light!", "");
            Tools.sound(level, o, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
            Scheduler.later(1200, () -> {
                for (BlockPos s : spots) {
                    String c = s.getX() + " " + s.getY() + " " + s.getZ();
                    Tools.command(level, "fill " + c + " " + c + " minecraft:air replace minecraft:light");
                }
            });
        });
    }

    private static void disco() {
        Egg.define("disco", "Disco", Egg.Category.WORLD, "A flashing, colorful dance floor with a beat.", "disco")
                .exact().run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos feet = ctx.player.blockPosition();
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(feet.getX() + dx, feet.getY() - 1, feet.getZ() + dz);
                    BlockState st = level.getBlockState(pos);
                    if (!st.isAir() && st.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
                            && !st.getCollisionShape(level, pos).isEmpty()) {
                        originals.put(pos, st);
                    }
                }
            }
            if (originals.isEmpty()) {
                ctx.reply("There's no floor to dance on here!");
                return;
            }
            String[] colors = {"red", "orange", "yellow", "lime", "light_blue", "magenta", "purple", "pink", "cyan", "white"};
            BlockState[] glass = new BlockState[colors.length];
            for (int i = 0; i < colors.length; i++) {
                glass[i] = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(colors[i] + "_stained_glass"))
                        .defaultBlockState();
            }
            int steps = 25;
            Scheduler.repeat(0, 8, steps, step -> {
                for (BlockPos pos : originals.keySet()) {
                    level.setBlock(pos, glass[Math.floorMod(step + pos.getX() + pos.getZ(), colors.length)], 3);
                }
                if (step % 2 == 0) Tools.sound(level, feet, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 1.0f);
                else Tools.sound(level, feet, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f + R.nextFloat());
            });
            Scheduler.later(steps * 8 + 4, () -> {
                for (Map.Entry<BlockPos, BlockState> e : originals.entrySet()) level.setBlock(e.getKey(), e.getValue(), 3);
            });
        });
    }

    private static void shake() {
        Egg.define("shake", "Earthquake", Egg.Category.WORLD, "The ground shakes and a few soft blocks break.",
                "earthquake", "earth quake").run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            for (ServerPlayer p : Tools.nearby(ctx, 12)) Tools.effect(level, p, "nausea", 6, 0);
            Scheduler.repeat(0, 10, 16, step -> {
                Tools.sound(level, o, SoundEvents.STONE_BREAK, 1.5f, 0.5f);
                Tools.sound(level, o, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
                Tools.particles(level, ParticleTypes.POOF, o.getX() + 0.5, o.getY() + 0.2, o.getZ() + 0.5, 25, 3.0, 0.05);
            });
            int limit = SHAKE_BLOCKS.asInt();
            List<String> soft = SHAKE_BREAKABLE.asList();
            int[] broken = {0};
            for (int i = 0; i < limit * 6; i++) {
                Scheduler.later(15 + i * 6, () -> {
                    if (broken[0] >= limit) return;
                    BlockPos pos = new BlockPos(o.getX() + R.nextInt(11) - 5, o.getY() - 1 + R.nextInt(3), o.getZ() + R.nextInt(11) - 5);
                    if (pos.getX() == o.getX() && pos.getZ() == o.getZ() && pos.getY() < o.getY()) return; // never under the player
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
                    if (!soft.contains(path)) return;
                    level.destroyBlock(pos, true);
                    broken[0]++;
                });
            }
        });
    }

    private static void meteor() {
        Egg.define("meteor", "Meteor", Egg.Category.WORLD, "A harmless meteor streaks down and leaves an obsidian rock.",
                "meteor").exact().run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            if (!level.canSeeSky(o)) {
                ctx.reply("You need a clear sky for a meteor!");
                return;
            }
            double angle = R.nextDouble() * Math.PI * 2;
            double dist = 10 + R.nextInt(5);
            int tx = o.getX() + (int) Math.round(Math.cos(angle) * dist);
            int tz = o.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int ty = ground(level, tx, tz);
            double sx = tx + 14;
            double sy = ty + 38;
            double sz = tz + 10;
            int ticks = 30;
            Tools.sound(level, o, SoundEvents.BLAZE_SHOOT, 2.0f, 0.5f);
            Scheduler.repeat(0, 1, ticks, i -> {
                double t = (i + 1) / (double) ticks;
                double x = sx + (tx + 0.5 - sx) * t;
                double y = sy + (ty - sy) * t;
                double z = sz + (tz + 0.5 - sz) * t;
                Tools.particles(level, ParticleTypes.FLAME, x, y, z, 4, 0.2, 0.02);
                Tools.particles(level, ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.2, 0.02);
            });
            Scheduler.later(ticks + 1, () -> {
                Tools.particles(level, ParticleTypes.EXPLOSION_EMITTER, tx + 0.5, ty + 1, tz + 0.5, 1, 0.0, 0.0);
                Tools.particles(level, ParticleTypes.FLAME, tx + 0.5, ty + 1, tz + 0.5, 40, 1.5, 0.1);
                Tools.sound(level, new BlockPos(tx, ty, tz), SoundEvents.ANVIL_LAND, 3.0f, 0.5f);
                BlockPos rock = new BlockPos(tx, ty, tz);
                if (level.getBlockState(rock).isAir()) level.setBlock(rock, Blocks.OBSIDIAN.defaultBlockState(), 3);
            });
        });
    }

    private static void aurora() {
        Egg.define("aurora", "Aurora", Egg.Category.WORLD, "Ribbons of colorful light across the sky.",
                "aurora", "northern lights").exact().run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            Scheduler.repeat(0, 4, 100, step -> {
                for (int k = 0; k < 48; k++) {
                    double a = k / 48.0 * Math.PI * 2;
                    double x = o.getX() + 0.5 + Math.cos(a) * 14;
                    double z = o.getZ() + 0.5 + Math.sin(a) * 14;
                    double y = o.getY() + 18 + Math.sin(a * 3 + step * 0.2) * 3;
                    Tools.particles(level, k % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ENCHANT, x, y, z, 1, 0.3, 0.02);
                }
            });
        });
    }

    private static void snowDay() {
        Egg.define("snowday", "Snow Day", Egg.Category.WORLD, "Snowfall, snowballs, and a snow golem friend.",
                "snow day").run(ctx -> {
            ServerLevel level = ctx.level;
            List<ServerPlayer> group = Tools.nearby(ctx, 12);
            for (ServerPlayer p : group) Tools.give(p, Tools.stack("snowball", 16));
            Tools.title(level, ctx.player, "Snow day!", "");
            Scheduler.repeat(0, 4, 60, step -> {
                for (ServerPlayer p : group) {
                    Tools.particles(level, ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 6, p.getZ(), 25, 4.0, 0.02);
                }
            });
            BlockPos spot = SafeSpot.findNear(level, ctx.player.blockPosition().offset(2, 0, 0), 3);
            if (spot != null) {
                Entity golem = EntityType.SNOW_GOLEM.create(level, EntitySpawnReason.COMMAND);
                if (golem != null) {
                    golem.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    level.addFreshEntity(golem);
                    Scheduler.later(2400, () -> {
                        if (!golem.isRemoved()) golem.discard();
                    });
                }
            }
        });
    }

    private static void zeus() {
        Egg.define("zeus", "Zeus", Egg.Category.WORLD, "Harmless lightning strikes all around you.", "zeus")
                .exact().run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            Tools.title(level, ctx.player, "Zeus approves!", "");
            for (int i = 0; i < 5; i++) {
                Scheduler.later(i * 12, () -> {
                    double a = R.nextDouble() * Math.PI * 2;
                    double d = 4 + R.nextInt(5);
                    Tools.lightning(level, o.getX() + 0.5 + Math.cos(a) * d, o.getY(), o.getZ() + 0.5 + Math.sin(a) * d);
                });
            }
        });
    }

    private static void tornado() {
        Egg.define("tornado", "Tornado", Egg.Category.WORLD, "A whirlwind that swirls nearby dropped items.",
                "tornado", "twister").exact().run(ctx -> {
            ServerLevel level = ctx.level;
            ServerPlayer p = ctx.player;
            Tools.sound(level, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, 2.0f, 0.6f);
            Scheduler.repeat(0, 2, 60, step -> {
                double cx = p.getX();
                double cy = p.getY();
                double cz = p.getZ();
                for (int k = 0; k < 20; k++) {
                    double a = step * 0.5 + k * 0.6;
                    double r = 0.5 + k * 0.15;
                    Tools.particles(level, ParticleTypes.CLOUD, cx + Math.cos(a) * r, cy + k * 0.3, cz + Math.sin(a) * r, 1, 0.05, 0.0);
                }
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(7.0))) {
                    double dx = item.getX() - cx;
                    double dz = item.getZ() - cz;
                    item.setDeltaMovement(new Vec3(-dz * 0.12, 0.28, dx * 0.12));
                }
            });
        });
    }

    private static void rainbow() {
        Egg.define("rainbow", "Rainbow", Egg.Category.WORLD, "A rainbow arcs across the sky.", "rainbow")
                .exact().run(ctx -> {
            ServerLevel level = ctx.level;
            Direction facing = ctx.player.getDirection();
            Direction right = facing.getClockWise();
            double cx = ctx.player.getX() + facing.getStepX() * 16;
            double cz = ctx.player.getZ() + facing.getStepZ() * 16;
            double cy = ctx.player.getY() + 2;
            Tools.title(level, ctx.player, "Rainbow!", "");
            Scheduler.repeat(0, 10, 24, step -> {
                for (int k = 0; k <= 40; k++) {
                    double t = Math.PI * k / 40.0;
                    double x = cx + right.getStepX() * Math.cos(t) * 13;
                    double z = cz + right.getStepZ() * Math.cos(t) * 13;
                    double y = cy + Math.sin(t) * 11;
                    // note particles take their colour from the "speed" slot, so the hue sweeps along the arc
                    level.sendParticles(ParticleTypes.NOTE, x, y, z, 0, k / 40.0, 0.0, 0.0, 1.0);
                }
            });
        });
    }

    private static void flowerRain() {
        String[] flowers = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium",
                "lily_of_the_valley", "blue_orchid", "red_tulip", "pink_tulip"};
        Egg.define("flowerrain", "Flower Rain", Egg.Category.WORLD, "Flowers fall gently around you.",
                "flower rain", "rain of flowers", "raining flowers").run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            Scheduler.repeat(0, 3, 24, step -> {
                for (int n = 0; n < 2; n++) {
                    int x = o.getX() + R.nextInt(9) - 4;
                    int z = o.getZ() + R.nextInt(9) - 4;
                    if (!Delivery.columnClear(level, x, o.getY(), z, 8)) continue;
                    ItemStack stack = Tools.stack(flowers[R.nextInt(flowers.length)], 1);
                    if (stack != null) level.addFreshEntity(new ItemEntity(level, x + 0.5, o.getY() + 8, z + 0.5, stack));
                }
                Tools.particles(level, ParticleTypes.CHERRY_LEAVES, ctx.player.getX(), ctx.player.getY() + 5, ctx.player.getZ(), 15, 3.0, 0.02);
            });
        });
    }

    private static void shootingStar() {
        String[][] wishes = {{"speed", "Speed"}, {"jump_boost", "Jump Boost"}, {"regeneration", "Regeneration"},
                {"night_vision", "Night Vision"}, {"haste", "Haste"}, {"luck", "Luck"}, {"strength", "Strength"},
                {"fire_resistance", "Fire Resistance"}, {"water_breathing", "Water Breathing"}};
        Egg.define("shootingstar", "Shooting Star", Egg.Category.WORLD, "A star streaks by and grants a small wish.",
                "shooting star", "wish upon a star", "falling star").run(ctx -> {
            ServerLevel level = ctx.level;
            BlockPos o = ctx.player.blockPosition();
            Scheduler.repeat(0, 1, 30, i -> {
                double t = i / 29.0;
                Tools.particles(level, ParticleTypes.END_ROD, o.getX() - 18 + 36 * t, o.getY() + 24 - 8 * t,
                        o.getZ() - 12 + 24 * t, 3, 0.1, 0.01);
            });
            Scheduler.later(32, () -> {
                String[] wish = wishes[R.nextInt(wishes.length)];
                Tools.effect(level, ctx.player, wish[0], 60, 0);
                Tools.title(level, ctx.player, "Make a wish!", "Your wish: " + wish[1]);
                Tools.sound(level, o, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.6f);
            });
        });
    }

    private static void beacon() {
        Egg.define("beacon", "Beacon of Light", Egg.Category.WORLD, "A beam of light shines on you for 15 seconds.",
                "beacon of light", "be my beacon", "beacon mode").run(ctx -> {
            ServerLevel level = ctx.level;
            ServerPlayer p = ctx.player;
            Tools.sound(level, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.0f);
            Scheduler.repeat(0, 2, 75, step -> {
                double x = p.getX();
                double z = p.getZ();
                double head = p.getY() + 2;
                // the beam starts well above the head so it never blocks the player's view
                for (int y = 3; y <= 32; y++) Tools.particles(level, ParticleTypes.END_ROD, x, head + y, z, 1, 0.05, 0.0);
                for (int k = 0; k < 12; k++) {
                    double a = k / 12.0 * Math.PI * 2 + step * 0.1;
                    Tools.particles(level, ParticleTypes.ENCHANT, x + Math.cos(a) * 2.5, p.getY() + 0.2, z + Math.sin(a) * 2.5, 1, 0.05, 0.02);
                }
            });
        });
    }
}
