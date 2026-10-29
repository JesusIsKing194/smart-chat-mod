package com.example.smartchat.engine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every item in the game (including other mods' items), built the first time it is needed. */
public final class ItemIndex {

    private static NameIndex<Item> instance;

    private ItemIndex() {}

    public static NameIndex<Item> get() {
        if (instance == null) instance = build();
        return instance;
    }

    private static NameIndex<Item> build() {
        NameIndex<Item> index = new NameIndex<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) continue;
            String path = id.getPath();
            index.add(path, item, priorityOf(path));
        }
        return index;
    }

    /** Lower number = preferred when a short name could mean several items ("netherite" -> ingot). */
    private static int priorityOf(String path) {
        if (path.endsWith("_ingot") || path.equals("diamond") || path.equals("emerald")) return 10;
        if (path.endsWith("_sword")) return 20;
        if (path.endsWith("_pickaxe")) return 21;
        if (path.endsWith("_axe")) return 22;
        if (path.endsWith("_shovel")) return 23;
        if (path.endsWith("_hoe")) return 24;
        if (path.endsWith("_helmet")) return 25;
        if (path.endsWith("_chestplate")) return 26;
        if (path.endsWith("_leggings")) return 27;
        if (path.endsWith("_boots")) return 28;
        if (path.endsWith("_block")) return 40;
        return 50;
    }

    /** Looks up an item by its exact id path, e.g. "netherite_chestplate". Returns null when it does not exist. */
    public static Item byPath(String path) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
        return item == Items.AIR ? null : item;
    }
}
