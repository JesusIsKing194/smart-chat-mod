package com.example.smartchat.engine;

import com.example.smartchat.util.NumberWords;
import com.example.smartchat.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Understands enchantment names, nicknames, typos and levels ("protection 4", "sharp v", "fast mining"). */
public final class EnchantIndex {

    /** Level value meaning "the highest level". */
    public static final int LEVEL_MAX = 255;

    /**
     * One enchantment found in a message.
     * level = -1 when no level was written after it; levelBefore = number directly before it (or -1).
     * Tokens start (inclusive) to end (exclusive) are the name plus its level.
     */
    public record Mention(String id, int level, int start, int end, int levelBefore, int levelBeforeIndex) {}

    private record Alias(String[] words, String id) {}

    private static final List<Alias> ALIASES = new ArrayList<>();
    private static final Map<String, Integer> MAX_LEVEL = new HashMap<>();

    static {
        a("efficiency", 5, "efficiency", "eff", "fast mining", "faster mining", "mining speed");
        a("sharpness", 5, "sharpness", "sharp");
        a("smite", 5, "smite");
        a("bane_of_arthropods", 5, "bane of arthropods", "bane");
        a("knockback", 2, "knockback", "kb");
        a("fire_aspect", 2, "fire aspect");
        a("looting", 3, "looting");
        a("sweeping_edge", 3, "sweeping edge", "sweeping");
        a("mending", 1, "mending");
        a("unbreaking", 3, "unbreaking", "unbreakable", "durable", "durability");
        a("fortune", 3, "fortune");
        a("silk_touch", 1, "silk touch", "silk");
        a("protection", 4, "protection", "prot");
        a("fire_protection", 4, "fire protection", "fire prot");
        a("blast_protection", 4, "blast protection", "blast prot");
        a("projectile_protection", 4, "projectile protection", "projectile prot", "proj prot");
        a("feather_falling", 4, "feather falling", "feather fall");
        a("respiration", 3, "respiration", "water breathing");
        a("aqua_affinity", 1, "aqua affinity");
        a("thorns", 3, "thorns");
        a("depth_strider", 3, "depth strider");
        a("frost_walker", 2, "frost walker");
        a("soul_speed", 3, "soul speed");
        a("swift_sneak", 3, "swift sneak");
        a("power", 5, "power");
        a("punch", 2, "punch");
        a("flame", 1, "flame");
        a("infinity", 1, "infinity");
        a("luck_of_the_sea", 3, "luck of the sea");
        a("lure", 3, "lure");
        a("loyalty", 3, "loyalty");
        a("impaling", 5, "impaling");
        a("riptide", 3, "riptide");
        a("channeling", 1, "channeling");
        a("multishot", 1, "multishot");
        a("quick_charge", 3, "quick charge");
        a("piercing", 4, "piercing");
        a("density", 5, "density");
        a("breach", 4, "breach");
        a("wind_burst", 3, "wind burst");
        a("lunge", 3, "lunge");
        a("vanishing_curse", 1, "curse of vanishing", "vanishing curse");
        a("binding_curse", 1, "curse of binding", "binding curse");
        // longest names first so "fire protection" wins over "protection"
        ALIASES.sort(Comparator.comparingInt((Alias x) -> x.words().length).reversed());
    }

    private EnchantIndex() {}

    private static void a(String id, int max, String... aliases) {
        MAX_LEVEL.put(id, max);
        for (String alias : aliases) {
            ALIASES.add(new Alias(TextUtil.normalize(alias).split(" "), id));
        }
    }

    /** Usual maximum level of the enchantment (used only to understand wording, never to limit it). */
    public static int maxLevel(String id) {
        return MAX_LEVEL.getOrDefault(id, 1);
    }

    private static boolean matches(List<String> tokens, int at, String[] words) {
        for (int k = 0; k < words.length; k++) {
            String t = tokens.get(at + k);
            String w = words[k];
            if (t.equals(w)) continue;
            // typo tolerance only for a single long word ("effcency", "shprness")
            if (words.length == 1 && w.length() >= 6 && t.length() >= 5 && t.charAt(0) == w.charAt(0)
                    && TextUtil.levenshtein(t, w) <= (w.length() >= 9 ? 2 : 1)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static Integer parseLevel(String t) {
        if (t.equals("max") || t.equals("maxed")) return LEVEL_MAX;
        Integer n = NumberWords.parse(t);
        if (n == null) n = NumberWords.roman(t);
        if (n == null || n < 1 || n > 255) return null;
        return n;
    }

    /** Finds every enchantment in the message. Tokens flagged in skip are ignored. */
    public static List<Mention> scan(List<String> tokens, boolean[] skip) {
        List<Mention> out = new ArrayList<>();
        int size = tokens.size();
        int i = 0;
        while (i < size) {
            if (skip != null && skip[i]) {
                i++;
                continue;
            }
            Alias hit = null;
            for (Alias alias : ALIASES) {
                int n = alias.words().length;
                if (i + n > size) continue;
                boolean blocked = false;
                if (skip != null) {
                    for (int k = 0; k < n; k++) {
                        if (skip[i + k]) {
                            blocked = true;
                            break;
                        }
                    }
                }
                if (!blocked && matches(tokens, i, alias.words())) {
                    hit = alias;
                    break;
                }
            }
            if (hit == null) {
                i++;
                continue;
            }
            int end = i + hit.words().length;
            int level = -1;
            int levelEnd = end;
            int j = end;
            if (j < size && (tokens.get(j).equals("level") || tokens.get(j).equals("lvl") || tokens.get(j).equals("lv"))) j++;
            Integer after = j < size && (skip == null || !skip[j]) ? parseLevel(tokens.get(j)) : null;
            int before = -1;
            int beforeIndex = -1;
            if (after != null) {
                level = after;
                levelEnd = j + 1;
            } else if (i > 0 && (skip == null || !skip[i - 1])) {
                Integer b = NumberWords.parse(tokens.get(i - 1));
                if (b != null && b >= 1 && b <= 255) {
                    before = b;
                    beforeIndex = i - 1;
                }
            }
            out.add(new Mention(hit.id(), level, i, levelEnd, before, beforeIndex));
            i = levelEnd;
        }
        return out;
    }
}
