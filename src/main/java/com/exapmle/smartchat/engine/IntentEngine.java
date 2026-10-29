package com.example.smartchat.engine;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.util.TextUtil;
import net.minecraft.world.entity.EntityType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Scores a message against every intent using weighted phrases, so "give me a sword at night"
 * is an item request (not a time change) and "teleport me plz" is understood as a teleport.
 */
public final class IntentEngine {

    private static final Map<Intent, Map<String, Double>> TABLE = new EnumMap<>(Intent.class);

    private static final String[] CUE_PHRASES = {
            "can i", "could i", "may i", "can you", "could you", "would you", "will you", "give me", "gimme",
            "give us", "get me", "i want", "i need", "i would like", "id like", "i wanna", "i will have", "lemme",
            "let me", "make me", "send me", "take me", "bring me", "hook me", "show me", "build me", "i require",
            "how about", "can we", "could we", "i d like", "i d love", "i would love"};

    private static final Set<String> IMPERATIVE_STARTS = new HashSet<>(Arrays.asList(
            "give", "spawn", "summon", "drop", "get", "make", "teleport", "tp", "rtp", "heal", "fly", "build", "open",
            "trap", "set", "change", "stop", "turn", "enable", "disable", "toggle", "skip", "gimme", "need", "want",
            "send", "take", "bring", "show", "let", "lemme", "xray", "stacked"));

    private static void w(Intent intent, double weight, String... phrases) {
        Map<String, Double> map = TABLE.computeIfAbsent(intent, k -> new LinkedHashMap<>());
        for (String p : phrases) map.put(TextUtil.normalize(p), weight);
    }

    static {
        w(Intent.TELEPORT, 4.0, "teleport me", "tp me", "tp to", "teleport to", "rtp", "random teleport", "random tp",
                "nether roof", "teleport", "send me to", "take me to", "warp me", "warp to", "tp");
        w(Intent.TELEPORT, 2.5, "warp", "travel to", "go to the nether roof", "biome", "nearest village", "nearest");
        w(Intent.TELEPORT, 1.5, "go to", "send me", "take me", "roof", "structure");

        w(Intent.WEATHER, 4.0, "make it rain", "let it rain", "stop the rain", "stop raining", "make it sunny",
                "clear the weather", "clear weather", "clear skies", "change the weather", "set the weather", "weather");
        w(Intent.WEATHER, 2.0, "thunder", "storm", "sunny");
        w(Intent.WEATHER, 1.2, "rain", "raining");
        w(Intent.WEATHER, 0.8, "sun");

        w(Intent.TIME, 4.0, "set time", "change time", "set the time", "change the time", "make it day", "make it night",
                "make it morning", "make it noon", "make it midnight", "skip the night", "skip night", "time to day",
                "time to night", "it be day", "it be night", "turn it to day", "turn it to night");
        w(Intent.TIME, 2.0, "sunrise", "sunset", "midnight", "noon");
        w(Intent.TIME, 0.8, "time");
        w(Intent.TIME, 0.6, "night");
        w(Intent.TIME, 0.3, "day");

        w(Intent.FLY, 4.0, "let me fly", "can i fly", "i want to fly", "turn on flight", "enable flight", "toggle flight",
                "creative flight", "stop flying", "fly mode", "turn on fly");
        w(Intent.FLY, 2.5, "fly", "flight");
        w(Intent.FLY, 1.5, "flying");

        w(Intent.SPEED, 4.0, "speed boost", "go faster", "run faster", "speed me up", "give me speed");
        w(Intent.SPEED, 2.0, "speed", "sprint");
        w(Intent.SPEED, 1.0, "faster", "zoom");

        w(Intent.HEAL, 4.0, "heal me", "full health", "restore health", "god mode", "godmode", "heal");
        w(Intent.HEAL, 2.0, "healing", "regenerate", "regen", "low health", "feed me");

        w(Intent.XRAY, 4.0, "xray", "x ray", "see through walls", "ore vision", "see ores");
        w(Intent.XRAY, 3.0, "find ores", "night vision");

        w(Intent.PORTAL, 5.0, "nether portal", "neth portal", "portal to the nether", "build a portal", "make a portal",
                "open a portal");
        w(Intent.PORTAL, 2.0, "portal");

        w(Intent.STACKED, 5.0, "stacked", "get stacked", "make me stacked", "stack me");

        w(Intent.TRAP, 4.0, "trap him", "trap her", "trap them");
        w(Intent.TRAP, 3.0, "trap");

        w(Intent.MENU, 5.0, "open settings", "open the settings", "smart chat menu", "smartchat menu", "open menu",
                "open smart chat", "how to use", "open help", "open how to use");
        w(Intent.MENU, 4.0, "what can you do", "help menu", "show me what you can do");
        w(Intent.MENU, 2.0, "settings");

        w(Intent.SUMMON, 4.0, "summon");
        w(Intent.SUMMON, 2.0, "spawn");
    }

    private IntentEngine() {}

    public static Intents classify(ChatContext ctx) {
        Map<Intent, Double> scores = new EnumMap<>(Intent.class);
        for (Map.Entry<Intent, Map<String, Double>> table : TABLE.entrySet()) {
            double total = 0;
            for (Map.Entry<String, Double> phrase : table.getValue().entrySet()) {
                String p = phrase.getKey();
                if (TextUtil.containsPhrase(ctx.text, p)) {
                    total += phrase.getValue();
                } else if (!p.contains(" ") && p.length() >= 6) {
                    for (String token : ctx.tokens) {
                        if (token.length() >= 5 && Math.abs(token.length() - p.length()) <= 1
                                && token.charAt(0) == p.charAt(0) && TextUtil.levenshtein(token, p) <= 1) {
                            total += phrase.getValue() * 0.8;
                            break;
                        }
                    }
                }
            }
            if (total > 0) scores.put(table.getKey(), total);
        }

        // "spawn a zombie" is a mob, "spawn a diamond sword" is an item
        if (scores.getOrDefault(Intent.SUMMON, 0.0) > 0) {
            NameIndex.Match<EntityType<?>> mob = MobIndex.get().find(ctx.text,
                    EngineSettings.FUZZY_THRESHOLD.asDouble(), 1);
            if (mob != null && mob.confidence() >= 0.9) {
                scores.merge(Intent.SUMMON, 2.5, Double::sum);
            }
        }

        return new Intents(scores, hasRequestCue(ctx));
    }

    /** True when the message sounds like someone asking for something. */
    public static boolean hasRequestCue(ChatContext ctx) {
        for (String cue : CUE_PHRASES) {
            if (ctx.has(cue)) return true;
        }
        if (!ctx.tokens.isEmpty() && IMPERATIVE_STARTS.contains(ctx.tokens.get(0))) return true;
        return ctx.polite && ctx.tokens.size() <= 12;
    }
}
