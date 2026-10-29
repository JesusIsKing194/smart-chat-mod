package com.example.smartchat.items;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;

/** Settings for item requests and how items are delivered. */
public final class ItemSettings {

    static {
        Settings.describeSection("items", "Item requests and drops");
    }

    public static final Setting ENABLED = Settings.bool("items", "enabled", true,
            "Lets players ask for items in chat (\"can i get a neth pick\").");

    public static final Setting REQUIRE_POLITE = Settings.bool("items", "require_polite", false,
            "When true, players must say please/thanks (see general.polite_words) to get items.");

    public static final Setting MAX_AMOUNT = Settings.integer("items", "max_amount", 64, 1, 2304,
            "Largest amount of one item a player can ask for at once.");

    public static final Setting MAX_STACKS = Settings.integer("items", "max_stacks", 36, 1, 128,
            "Largest number of item stacks handed out in a single request.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("items", "cooldown_seconds", 0, 0, 3600,
            "Seconds a player must wait between item requests (0 = no wait).");

    public static final Setting DROP_RADIUS = Settings.integer("items", "drop_radius", 1, 0, 3,
            "Dropped items land within this many blocks of the player. 1 means a 3x3 area, 0 means right on top of them.");

    public static final Setting DROP_HEIGHT = Settings.integer("items", "drop_height", 3, 1, 12,
            "How many blocks above the player dropped items start falling from.");

    public static final Setting BLOCK_DROP_HEIGHT = Settings.integer("items", "block_drop_height", 8, 2, 20,
            "How high usable blocks (crafting tables, anvils, chests...) start falling from.");

    public static final Setting FALLBACK_TO_INVENTORY = Settings.bool("items", "fallback_to_inventory", true,
            "If something blocks the drop (a cave ceiling, for example), the item goes straight into the player's inventory.");

    public static final Setting PLACE_USABLE_BLOCKS = Settings.bool("items", "place_usable_blocks", true,
            "Usable blocks (crafting tables, anvils, chests, furnaces...) fall from the sky and place themselves.");

    public static final Setting OVER_MAX_LEVELS = Settings.bool("items", "allow_over_max_levels", false,
            "Allow enchantment levels above the normal maximum (up to 255).");

    public static final Setting ENCHANT_ANY = Settings.bool("items", "enchant_unsupported", false,
            "Allow enchantments that normally cannot go on that item (for example Sharpness on a pickaxe).");

    public static final Setting EFFECTS = Settings.bool("items", "effects", true,
            "Plays a chime and glowing particles when items arrive.");

    public static final Setting BLACKLIST = Settings.list("items", "blacklist",
            "bedrock,command_block,chain_command_block,repeating_command_block,command_block_minecart,barrier,"
                    + "structure_block,structure_void,jigsaw,light,debug_stick,end_portal_frame,spawner,trial_spawner,vault,"
                    + "reinforced_deepslate",
            "Items nobody can ask for (use item ids, comma separated).");

    private ItemSettings() {}

    public static void init() {}
}
