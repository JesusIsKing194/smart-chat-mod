package com.example.smartchat.stacked;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;

/** Settings for the "get stacked" command. */
public final class StackedSettings {

    static {
        Settings.describeSection("stacked", "Get stacked");
    }

    public static final Setting ENABLED = Settings.bool("stacked", "enabled", true,
            "\"can i get stacked please\" drops three loot shulker boxes in front of you.");

    public static final Setting PUNISH = Settings.bool("stacked", "punish_rude", true,
            "Asking without being polite rains TNT and anvils around you instead. Turn off to just ask them to say please.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("stacked", "cooldown_seconds", 20, 0, 3600,
            "Seconds a player must wait between stacked requests.");

    public static final Setting DISTANCE = Settings.integer("stacked", "distance_in_front", 2, 1, 6,
            "How many blocks in front of the player the shulker boxes land.");

    public static final Setting BOX_HEIGHT = Settings.integer("stacked", "box_drop_height", 8, 3, 20,
            "How high the shulker boxes start falling from.");

    public static final Setting PUNISH_RADIUS = Settings.integer("stacked", "punish_radius", 4, 1, 8,
            "Radius of the TNT and anvil rain (4 = an 8x8 area).");

    public static final Setting TNT_COUNT = Settings.integer("stacked", "tnt_count", 6, 0, 40,
            "How many primed TNT blocks fall during the punishment.");

    public static final Setting TNT_FUSE = Settings.integer("stacked", "tnt_fuse_ticks", 80, 10, 400,
            "Fuse length of the TNT in ticks (20 ticks = 1 second).");

    public static final Setting ANVIL_COUNT = Settings.integer("stacked", "anvil_count", 8, 0, 40,
            "How many damaging anvils fall during the punishment.");

    public static final Setting ANVIL_DAMAGE = Settings.decimal("stacked", "anvil_damage_per_block", 2.0, 0.0, 20.0,
            "Damage an anvil deals per block it fell (vanilla anvils use 2).");

    public static final Setting PUNISH_HEIGHT = Settings.integer("stacked", "punish_height", 12, 4, 24,
            "How high the TNT and anvils start falling from.");

    public static final Setting BOX_1 = Settings.list("stacked", "box_1_gear",
            "netherite_helmet,netherite_chestplate,netherite_leggings,netherite_boots,netherite_sword,netherite_pickaxe,"
                    + "netherite_axe,netherite_shovel,netherite_hoe,bow,crossbow,trident,shield,elytra,mace,fishing_rod",
            "Contents of the first box. Use item ids; add *amount for more than one. Gear comes fully enchanted.");

    public static final Setting BOX_2 = Settings.list("stacked", "box_2_supplies",
            "enchanted_golden_apple*64,golden_apple*64,totem_of_undying*4,ender_pearl*16,firework_rocket*64,"
                    + "cooked_beef*64,golden_carrot*64,experience_bottle*64,water_bucket,lava_bucket,arrow*64,"
                    + "spectral_arrow*64,wind_charge*64",
            "Contents of the second box.");

    public static final Setting BOX_3 = Settings.list("stacked", "box_3_blocks",
            "netherite_block*9,diamond_block*64,emerald_block*64,gold_block*64,iron_block*64,"
                    + "netherite_upgrade_smithing_template*8,beacon,nether_star*4,ancient_debris*64,obsidian*64,ender_chest",
            "Contents of the third box.");

    private StackedSettings() {}

    public static void init() {}
}
