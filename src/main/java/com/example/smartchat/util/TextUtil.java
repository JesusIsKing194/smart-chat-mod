package com.example.smartchat.util;

import com.example.smartchat.config.Settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Text helpers shared by the whole chat engine. */
public final class TextUtil {

    private TextUtil() {}

    /** Lower-case, apostrophes removed, everything except a-z and 0-9 turned into single spaces. */
    public static String normalize(String input) {
        if (input == null) return "";
        String t = input.toLowerCase(Locale.ROOT).replace("\u2019", "").replace("'", "");
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
            else sb.append(' ');
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    public static List<String> tokens(String normalized) {
        if (normalized.isEmpty()) return List.of();
        return Arrays.asList(normalized.split(" "));
    }

    /** True when the whole phrase appears in the (already normalized) text, on word boundaries. */
    public static boolean containsPhrase(String normalizedText, String phrase) {
        String p = normalize(phrase);
        if (p.isEmpty()) return false;
        return (" " + normalizedText + " ").contains(" " + p + " ");
    }

    public static boolean containsAny(String normalizedText, String... phrases) {
        for (String phrase : phrases) {
            if (containsPhrase(normalizedText, phrase)) return true;
        }
        return false;
    }

    /** Removes a whole phrase from normalized text. */
    public static String removePhrase(String normalizedText, String phrase) {
        String p = normalize(phrase);
        if (p.isEmpty()) return normalizedText;
        String padded = (" " + normalizedText + " ").replace(" " + p + " ", " ");
        return padded.trim().replaceAll("\\s+", " ");
    }

    /** Polite if any word from the polite_words setting appears (small typos such as "pleaseee" still count). */
    public static boolean isPolite(String normalizedText) {
        for (String entry : Settings.POLITE_WORDS.asList()) {
            String p = normalize(entry);
            if (p.isEmpty()) continue;
            if (containsPhrase(normalizedText, p)) return true;
            if (!p.contains(" ") && p.length() >= 3) {
                int allowed = p.length() >= 8 ? 2 : (p.length() >= 5 ? 1 : 0);
                if (allowed == 0) continue;
                for (String token : normalizedText.split(" ")) {
                    if (Math.abs(token.length() - p.length()) <= allowed && levenshtein(token, p) <= allowed) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    /** 1.0 = identical, 0.0 = nothing in common. */
    public static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / max;
    }
}
