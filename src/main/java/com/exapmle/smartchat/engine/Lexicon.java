package com.example.smartchat.engine;

import com.example.smartchat.util.TextUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Slang, filler words and spelling shortcuts used before names are matched. */
public final class Lexicon {

    /** Words that carry no meaning for finding an item or mob. ("of" is deliberately NOT here: "totem of undying"). */
    public static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "a", "an", "the", "some", "any", "can", "could", "would", "will", "may", "might", "shall", "you", "your",
            "i", "me", "my", "mine", "we", "us", "please", "plz", "pls", "kindly", "thanks", "thank", "thx", "ty",
            "get", "give", "gimme", "have", "want", "need", "like", "wanna", "lemme", "let", "just", "for", "to",
            "with", "and", "also", "something", "ill", "id", "im", "ive", "it", "this", "that", "make", "spawn",
            "drop", "hook", "up", "bring", "send", "show", "build", "now", "quickly", "fast", "is", "are", "be",
            "do", "does", "did", "got", "enchant", "enchants", "enchanted", "pair", "sum", "bit", "little"));

    /** Quantity words (not part of a name). */
    public static final Set<String> QUANTITY_WORDS = new HashSet<>(Arrays.asList(
            "stack", "stacks", "dozen", "couple", "few", "several", "bunch", "lots", "tons", "loads", "half"));

    private static final Map<String, String> SLANG = new HashMap<>();
    private static final Set<String> TOOL_NOUNS = new HashSet<>(Arrays.asList(
            "sword", "pickaxe", "axe", "shovel", "hoe"));
    private static final Set<String> ARMOR_NOUNS = new HashSet<>(Arrays.asList(
            "helmet", "chestplate", "leggings", "boots"));

    static {
        s("dia", "diamond");
        s("diamonds", "diamond");
        s("neth", "netherite");
        s("nethrite", "netherite");
        s("obby", "obsidian");
        s("obi", "obsidian");
        s("cobble", "cobblestone");
        s("pick", "pickaxe");
        s("pic", "pickaxe");
        s("pickax", "pickaxe");
        s("swrd", "sword");
        s("sord", "sword");
        s("shov", "shovel");
        s("helm", "helmet");
        s("pants", "leggings");
        s("legs", "leggings");
        s("boot", "boots");
        s("gapple", "golden apple");
        s("gapples", "golden apple");
        s("godapple", "enchanted golden apple");
        s("egap", "enchanted golden apple");
        s("egapple", "enchanted golden apple");
        s("notch", "enchanted golden apple");
        s("xp", "experience bottle");
        s("xbow", "crossbow");
        s("firework", "firework rocket");
        s("fireworks", "firework rocket");
        s("shulker", "shulker box");
        s("steak", "cooked beef");
        s("porkchop", "cooked porkchop");
        s("workbench", "crafting table");
        s("elytras", "elytra");
        s("tnt", "tnt");
    }

    private Lexicon() {}

    private static void s(String from, String to) {
        SLANG.put(from, to);
    }

    /** Expands slang and fixes common material spellings in normalized text. */
    public static String expand(String normalized) {
        List<String> in = TextUtil.tokens(normalized);
        boolean hasUndying = in.contains("undying");
        boolean hasEnder = in.contains("ender");
        boolean hasBox = in.contains("box");

        // pass 1: single word slang and two word fixes
        List<String> pass1 = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            String t = in.get(i);
            String next = i + 1 < in.size() ? in.get(i + 1) : "";
            if (t.equals("chest") && next.equals("plate")) {
                pass1.add("chestplate");
                i++;
                continue;
            }
            if ((t.equals("totem") || t.equals("totems")) && !hasUndying) {
                pass1.addAll(Arrays.asList("totem", "of", "undying"));
                continue;
            }
            if ((t.equals("pearl") || t.equals("pearls")) && !hasEnder) {
                pass1.addAll(Arrays.asList("ender", "pearl"));
                continue;
            }
            if (t.equals("shulker") && hasBox) {
                pass1.add("shulker");
                continue;
            }
            String rep = SLANG.get(t);
            if (rep != null) {
                pass1.addAll(Arrays.asList(rep.split(" ")));
            } else {
                pass1.add(t);
            }
        }

        // pass 2: gold -> golden, wood -> wooden, chain -> chainmail when followed by gear
        List<String> out = new ArrayList<>();
        for (int i = 0; i < pass1.size(); i++) {
            String t = pass1.get(i);
            String next = i + 1 < pass1.size() ? pass1.get(i + 1) : "";
            boolean gear = TOOL_NOUNS.contains(next) || ARMOR_NOUNS.contains(next);
            if (t.equals("gold") && (gear || next.equals("apple") || next.equals("carrot") || next.equals("horse"))) {
                out.add("golden");
            } else if (t.equals("wood") && TOOL_NOUNS.contains(next)) {
                out.add("wooden");
            } else if (t.equals("chain") && ARMOR_NOUNS.contains(next)) {
                out.add("chainmail");
            } else {
                out.add(t);
            }
        }
        return String.join(" ", out);
    }

    /** Very small singularizer: diamonds -> diamond, apples -> apple, batteries -> battery. */
    public static String singular(String token) {
        if (token.length() <= 3) return token;
        if (token.endsWith("ies") && token.length() > 4) return token.substring(0, token.length() - 3) + "y";
        if (token.endsWith("ches") || token.endsWith("shes") || token.endsWith("xes") || token.endsWith("sses")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("ss") || token.endsWith("us")) return token;
        if (token.endsWith("s")) return token.substring(0, token.length() - 1);
        return token;
    }
}
