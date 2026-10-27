package com.example.smartchat.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of every setting. Features create their settings with the factory methods
 * (usually as static fields) BEFORE the config file is loaded.
 */
public final class Settings {

    private static final Map<String, Setting> ALL = new LinkedHashMap<>();
    private static final Map<String, String> SECTION_TITLES = new LinkedHashMap<>();

    private Settings() {}

    // ---------------- factories ----------------

    public static Setting bool(String section, String key, boolean def, String description) {
        return add(new Setting(section, key, Setting.Type.BOOLEAN, Boolean.toString(def), 0, 0, description));
    }

    public static Setting integer(String section, String key, int def, int min, int max, String description) {
        return add(new Setting(section, key, Setting.Type.INTEGER, Integer.toString(def), min, max, description));
    }

    public static Setting decimal(String section, String key, double def, double min, double max, String description) {
        return add(new Setting(section, key, Setting.Type.DECIMAL, Double.toString(def), min, max, description));
    }

    public static Setting text(String section, String key, String def, String description) {
        return add(new Setting(section, key, Setting.Type.TEXT, def, 0, 0, description));
    }

    public static Setting list(String section, String key, String def, String description) {
        return add(new Setting(section, key, Setting.Type.LIST, def, 0, 0, description));
    }

    private static Setting add(Setting s) {
        ALL.put(s.id(), s);
        SECTION_TITLES.putIfAbsent(s.section, s.section);
        return s;
    }

    public static void describeSection(String section, String title) {
        SECTION_TITLES.put(section, title);
    }

    // ---------------- lookups ----------------

    public static Collection<Setting> all() {
        return ALL.values();
    }

    public static Setting get(String id) {
        return ALL.get(id);
    }

    public static List<String> sections() {
        return new ArrayList<>(SECTION_TITLES.keySet());
    }

    public static String sectionTitle(String section) {
        return SECTION_TITLES.getOrDefault(section, section);
    }

    public static List<Setting> inSection(String section) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : ALL.values()) {
            if (s.section.equals(section)) out.add(s);
        }
        return out;
    }

    /** Touching this method makes sure the core settings below exist. */
    public static void init() {}

    // ---------------- core settings ----------------

    static {
        describeSection("general", "General");
    }

    public static final Setting ENABLED = bool("general", "enabled", true,
            "Master switch. Set to false to turn all of the mod's chat features off.");

    public static final Setting CHAT_COOLDOWN_MS = integer("general", "chat_cooldown_ms", 750, 0, 60000,
            "Minimum milliseconds between handled requests from the same player (stops spam).");

    public static final Setting MAX_MESSAGE_LENGTH = integer("general", "max_message_length", 150, 20, 500,
            "Messages longer than this are ignored by the mod.");

    public static final Setting POLITE_WORDS = list("general", "polite_words",
            "please,plz,pls,kindly,thanks,thank you,thx,ty,tysm,pretty please,would you mind",
            "Words or phrases (comma separated) that count as being polite.");

    public static final Setting ADMIN_NAMES = list("general", "admin_names", "",
            "Player names (comma separated) treated as admins for the settings menu. Server operators always count.");

    public static final Setting AI_REPLIES = bool("general", "ai_replies", true,
            "Lets the built-in assistant answer chat, jokes and small talk.");

    public static final Setting HINTS = bool("general", "hints", true,
            "When a request is not understood, the assistant gives a short hint instead of staying silent.");
}
