package com.example.smartchat.engine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Every summonable entity type, built the first time it is needed. */
public final class MobIndex {

    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "player", "fishing_bobber", "lightning_bolt", "area_effect_cloud", "marker", "interaction",
            "block_display", "item_display", "text_display", "item", "falling_block", "ominous_item_spawner"));

    private static NameIndex<EntityType<?>> instance;

    private MobIndex() {}

    public static NameIndex<EntityType<?>> get() {
        if (instance == null) instance = build();
        return instance;
    }

    private static NameIndex<EntityType<?>> build() {
        NameIndex<EntityType<?>> index = new NameIndex<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String path = id.getPath();
            if (SKIP.contains(path)) continue;
            index.add(path, BuiltInRegistries.ENTITY_TYPE.getValue(id), 50);
        }
        return index;
    }
}
