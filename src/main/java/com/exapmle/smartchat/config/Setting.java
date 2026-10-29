package com.example.smartchat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One configurable option. Shown in the config file and (later) in the in-game settings menu. */
public final class Setting {

    public enum Type { BOOLEAN, INTEGER, DECIMAL, TEXT, LIST }

    public final String section;
    public final String key;
    public final String description;
    public final Type type;
    public final String defaultValue;
    public final double min;
    public final double max;
    private String value;

    Setting(String section, String key, Type type, String defaultValue, double min, double max, String description) {
        this.section = section;
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.description = description;
        this.value = defaultValue;
    }

    public String id() {
        return section + "." + key;
    }

    public String raw() {
        return value;
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(value.trim());
    }

    public int asInt() {
        try {
            return (int) Math.round(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return (int) Math.round(Double.parseDouble(defaultValue));
        }
    }

    public double asDouble() {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return Double.parseDouble(defaultValue);
        }
    }

    public String asText() {
        return value;
    }

    /** Comma separated list, trimmed and lower-cased. */
    public List<String> asList() {
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String t = part.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Returns false (and keeps the old value) when the text is not valid for this setting. */
    public boolean set(String raw) {
        String cleaned = validate(raw);
        if (cleaned == null) return false;
        value = cleaned;
        return true;
    }

    public void reset() {
        value = defaultValue;
    }

    public String rangeNote() {
        if (type == Type.INTEGER) return "  (allowed: " + (long) min + " to " + (long) max + ")";
        if (type == Type.DECIMAL) return "  (allowed: " + min + " to " + max + ")";
        return "";
    }

    private String validate(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        switch (type) {
            case BOOLEAN:
                if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("on") || t.equalsIgnoreCase("yes")) return "true";
                if (t.equalsIgnoreCase("false") || t.equalsIgnoreCase("off") || t.equalsIgnoreCase("no")) return "false";
                return null;
            case INTEGER:
                try {
                    double d = Double.parseDouble(t);
                    return Integer.toString((int) Math.round(Math.max(min, Math.min(max, d))));
                } catch (NumberFormatException e) {
                    return null;
                }
            case DECIMAL:
                try {
                    double d = Double.parseDouble(t);
                    return Double.toString(Math.max(min, Math.min(max, d)));
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return t;
        }
    }
}
