package com.example.smartchat.config;

import com.example.smartchat.SmartChatSpawnerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes config/smartchat/settings.cfg (a commented, easy to edit text file). */
public final class ConfigManager {

    private static Path file;
    private static long lastModified;
    private static int counter;

    private ConfigManager() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("smartchat");
    }

    public static void load() {
        try {
            Files.createDirectories(dir());
            file = dir().resolve("settings.cfg");
            if (Files.exists(file)) read();
            write(); // adds any new settings and refreshes the comments
        } catch (IOException e) {
            SmartChatSpawnerMod.LOGGER.error("Could not load settings", e);
        }
    }

    /** Called by the in-game menu after a setting was changed. */
    public static void save() {
        try {
            write();
        } catch (IOException e) {
            SmartChatSpawnerMod.LOGGER.error("Could not save settings", e);
        }
    }

    public static void reload() {
        try {
            if (file != null && Files.exists(file)) read();
            write();
        } catch (IOException e) {
            SmartChatSpawnerMod.LOGGER.error("Could not reload settings", e);
        }
    }

    /** Checks every ~5 seconds whether the file was edited, and reloads it automatically. */
    public static void tick() {
        if (++counter < 100) return;
        counter = 0;
        try {
            if (file != null && Files.exists(file)) {
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (modified != lastModified) {
                    read();
                    lastModified = modified;
                    SmartChatSpawnerMod.LOGGER.info("Settings file changed, reloaded.");
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void read() throws IOException {
        String section = "";
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.startsWith("[") && t.endsWith("]")) {
                section = t.substring(1, t.length() - 1).trim().toLowerCase();
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) continue;
            String key = t.substring(0, eq).trim().toLowerCase();
            String value = t.substring(eq + 1).trim();
            Setting setting = Settings.get(section + "." + key);
            if (setting != null && !setting.set(value)) {
                SmartChatSpawnerMod.LOGGER.warn("Invalid value for {}: {} (keeping {})", setting.id(), value, setting.raw());
            }
        }
        lastModified = Files.getLastModifiedTime(file).toMillis();
    }

    private static void write() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# ==========================================================\n");
        sb.append("# Smart Chat Spawner Mod - settings\n");
        sb.append("# Lines starting with # are notes. Change the value after the = sign.\n");
        sb.append("# true / false = on / off.  Lists are separated by commas.\n");
        sb.append("# Changes load automatically a few seconds after you save this file.\n");
        sb.append("# ==========================================================\n\n");
        for (String section : Settings.sections()) {
            sb.append("[").append(section).append("]\n");
            sb.append("# ---- ").append(Settings.sectionTitle(section)).append(" ----\n\n");
            for (Setting s : Settings.inSection(section)) {
                sb.append("# ").append(s.description).append("\n");
                sb.append("# Default: ").append(s.defaultValue).append(s.rangeNote()).append("\n");
                sb.append(s.key).append(" = ").append(s.raw()).append("\n\n");
            }
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        lastModified = Files.getLastModifiedTime(file).toMillis();
    }
}
