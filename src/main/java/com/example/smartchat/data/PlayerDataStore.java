package com.example.smartchat.data;

import com.example.smartchat.SmartChatSpawnerMod;
import com.example.smartchat.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Loads and saves PlayerRecords. Always used from the server thread. */
public final class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, PlayerRecord> data = new HashMap<>();
    private static Path file;
    private static boolean dirty;
    private static int counter;

    private PlayerDataStore() {}

    public static void load() {
        try {
            Files.createDirectories(ConfigManager.dir());
            file = ConfigManager.dir().resolve("players.json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, PlayerRecord>>() {}.getType();
                    Map<String, PlayerRecord> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) data = loaded;
                }
            }
        } catch (Exception e) {
            SmartChatSpawnerMod.LOGGER.error("Could not load player data", e);
        }
        for (PlayerRecord record : data.values()) record.fix();
    }

    public static PlayerRecord get(ServerPlayer player) {
        PlayerRecord record = data.get(player.getUUID().toString());
        if (record == null) {
            record = new PlayerRecord();
            data.put(player.getUUID().toString(), record);
            dirty = true;
        }
        String name = player.getName().getString();
        if (!name.equals(record.name)) {
            record.name = name;
            dirty = true;
        }
        return record;
    }

    public static Map<String, PlayerRecord> all() {
        return Collections.unmodifiableMap(data);
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Returns true the first time this player discovers the egg. */
    public static boolean discoverEgg(ServerPlayer player, String eggId) {
        PlayerRecord record = get(player);
        boolean added = record.eggs.add(eggId);
        if (added) dirty = true;
        return added;
    }

    public static boolean hasEgg(ServerPlayer player, String eggId) {
        return get(player).eggs.contains(eggId);
    }

    /** Saves about every 30 seconds when something changed. */
    public static void tick() {
        if (!dirty) return;
        if (++counter >= 600) save();
    }

    public static void save() {
        counter = 0;
        if (file == null) return;
        try {
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            SmartChatSpawnerMod.LOGGER.error("Could not save player data", e);
        }
    }
}
