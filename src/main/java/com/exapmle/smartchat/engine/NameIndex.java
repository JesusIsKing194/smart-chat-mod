package com.example.smartchat.engine;

import com.example.smartchat.util.NumberWords;
import com.example.smartchat.util.TextUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the best named thing (item, mob, ...) inside a chat message.
 * Order of attempts: exact phrase, all words present, typo matching, then "starts with" (e.g. "totem").
 */
public final class NameIndex<T> {

    public record Match<T>(T value, String key, double confidence, List<String> alternatives, boolean ambiguous) {}

    private final Map<String, T> byKey = new LinkedHashMap<>();
    private final Map<String, String[]> tokensOf = new HashMap<>();
    private final Map<String, String> paddedKey = new HashMap<>();
    private final Map<String, Integer> priority = new HashMap<>();
    private final Map<String, List<String>> byWord = new HashMap<>();

    public void add(String key, T value, int prio) {
        String k = TextUtil.normalize(key);
        if (k.isEmpty() || byKey.containsKey(k)) return;
        byKey.put(k, value);
        String[] toks = k.split(" ");
        tokensOf.put(k, toks);
        paddedKey.put(k, " " + k + " ");
        priority.put(k, prio);
        for (String t : toks) byWord.computeIfAbsent(t, x -> new ArrayList<>()).add(k);
    }

    public T get(String key) {
        return byKey.get(TextUtil.normalize(key));
    }

    public int size() {
        return byKey.size();
    }

    private int words(String key) {
        return tokensOf.get(key).length;
    }

    private int rankCompare(String a, String b) {
        int wa = words(a), wb = words(b);
        if (wa != wb) return Integer.compare(wb, wa);
        return Integer.compare(priority.get(a), priority.get(b));
    }

    public Match<T> find(String text, double fuzzyThreshold, int maxAlternatives) {
        if (text == null || text.isBlank()) return null;
        List<String> tokens = TextUtil.tokens(text);
        List<String> singular = new ArrayList<>();
        for (String t : tokens) singular.add(Lexicon.singular(t));
        String[] padded = {" " + String.join(" ", tokens) + " ", " " + String.join(" ", singular) + " "};
        List<Set<String>> sets = List.of(new HashSet<>(tokens), new HashSet<>(singular));

        // A: the whole name appears as a phrase
        String best = null;
        for (String key : byKey.keySet()) {
            String needle = paddedKey.get(key);
            if (padded[0].contains(needle) || padded[1].contains(needle)) {
                if (best == null) {
                    best = key;
                } else {
                    int wa = words(key), wb = words(best);
                    if (wa > wb || (wa == wb && key.length() > best.length())
                            || (wa == wb && key.length() == best.length() && priority.get(key) < priority.get(best))) {
                        best = key;
                    }
                }
            }
        }
        if (best != null) return new Match<>(byKey.get(best), best, 1.0, List.of(), false);

        // B: every word of the name is somewhere in the message (any order)
        String bestB = null;
        List<String> tied = new ArrayList<>();
        for (String key : byKey.keySet()) {
            String[] kt = tokensOf.get(key);
            boolean all = false;
            for (Set<String> s : sets) {
                boolean ok = true;
                for (String t : kt) {
                    if (!s.contains(t)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    all = true;
                    break;
                }
            }
            if (!all) continue;
            if (bestB == null) {
                bestB = key;
                continue;
            }
            int c = rankCompare(key, bestB);
            if (c < 0) {
                bestB = key;
                tied.clear();
            } else if (c == 0) {
                if (key.length() < bestB.length()) {
                    tied.add(bestB);
                    bestB = key;
                } else {
                    tied.add(key);
                }
            }
        }
        if (bestB != null) {
            List<String> alts = new ArrayList<>(tied);
            if (alts.size() > maxAlternatives) alts = new ArrayList<>(alts.subList(0, maxAlternatives));
            return new Match<>(byKey.get(bestB), bestB, 0.95, alts, !tied.isEmpty());
        }

        // words without filler
        List<String> stripped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<String> list : List.of(tokens, singular)) {
            for (String t : list) {
                if (Lexicon.STOP.contains(t) || t.equals("of") || NumberWords.parse(t) != null) continue;
                if (seen.add(t)) stripped.add(t);
            }
        }

        // C: typo matching on groups of 1 to 4 words
        Set<String> grams = new LinkedHashSet<>();
        for (List<String> list : List.of(tokens, singular)) {
            List<String> clean = new ArrayList<>();
            for (String t : list) {
                if (!Lexicon.STOP.contains(t) && NumberWords.parse(t) == null) clean.add(t);
            }
            for (int i = 0; i < clean.size(); i++) {
                StringBuilder sb = new StringBuilder();
                for (int n = 0; n < 4 && i + n < clean.size(); n++) {
                    if (n > 0) sb.append(' ');
                    sb.append(clean.get(i + n));
                    grams.add(sb.toString());
                }
            }
        }
        Map<String, Double> sims = new HashMap<>();
        for (String g : grams) {
            if (g.length() < 4) continue;
            for (String key : byKey.keySet()) {
                int diff = Math.abs(key.length() - g.length());
                if (diff > Math.max(2, g.length() / 4)) continue;
                double sim = TextUtil.similarity(g, key);
                if (sim >= fuzzyThreshold) sims.merge(key, sim, Math::max);
            }
        }
        if (!sims.isEmpty()) {
            List<String> ranked = new ArrayList<>(sims.keySet());
            ranked.sort((a, b) -> {
                int c = Double.compare(sims.get(b), sims.get(a));
                if (c != 0) return c;
                return Integer.compare(priority.get(a), priority.get(b));
            });
            String top = ranked.get(0);
            double topSim = sims.get(top);
            List<String> alts = new ArrayList<>();
            boolean tie = false;
            for (int i = 1; i < ranked.size() && alts.size() < maxAlternatives; i++) {
                String other = ranked.get(i);
                double s = sims.get(other);
                if (s >= topSim - 0.02) {
                    alts.add(other);
                    if (s >= topSim - 1e-9 && priority.get(other).equals(priority.get(top))) tie = true;
                }
            }
            return new Match<>(byKey.get(top), top, topSim, alts, tie);
        }

        // D: a word is the beginning of a name (e.g. "totem" -> "totem of undying")
        Map<String, Integer> matched = new HashMap<>();
        for (String t : stripped) {
            if (t.length() < 3) continue;
            Set<String> keysForToken = new HashSet<>();
            for (Map.Entry<String, List<String>> e : byWord.entrySet()) {
                if (e.getKey().startsWith(t)) keysForToken.addAll(e.getValue());
            }
            for (String k : keysForToken) matched.merge(k, 1, Integer::sum);
        }
        if (matched.isEmpty()) return null;
        List<String> cands = new ArrayList<>(matched.keySet());
        cands.sort((a, b) -> {
            int ma = matched.get(a), mb = matched.get(b);
            if (ma != mb) return Integer.compare(mb, ma);
            int wa = words(a), wb = words(b);
            if (wa != wb) return Integer.compare(wa, wb);
            int pa = priority.get(a), pb = priority.get(b);
            if (pa != pb) return Integer.compare(pa, pb);
            return Integer.compare(a.length(), b.length());
        });
        String top = cands.get(0);
        boolean amb = false;
        if (cands.size() > 1) {
            String second = cands.get(1);
            amb = matched.get(top).equals(matched.get(second)) && words(top) == words(second)
                    && priority.get(top).equals(priority.get(second));
        }
        List<String> alts = new ArrayList<>();
        for (int i = 1; i < cands.size() && alts.size() < maxAlternatives; i++) alts.add(cands.get(i));
        return new Match<>(byKey.get(top), top, 0.7, alts, amb);
    }
}
