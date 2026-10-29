package com.example.smartchat.engine;

import java.util.EnumMap;
import java.util.Map;

/** Scores for every intent plus whether the message sounds like a request. */
public final class Intents {

    /** Score needed when the message clearly sounds like a request ("can i...", "give me...", "please"). */
    public static final double THRESHOLD_WITH_CUE = 2.5;
    /** Score needed otherwise (so ordinary chat like "I love to fly" is left alone). */
    public static final double THRESHOLD_WITHOUT_CUE = 4.0;

    public final Map<Intent, Double> scores;
    public final boolean requestCue;

    Intents(Map<Intent, Double> scores, boolean requestCue) {
        this.scores = new EnumMap<>(Intent.class);
        this.scores.putAll(scores);
        this.requestCue = requestCue;
    }

    public double score(Intent intent) {
        return scores.getOrDefault(intent, 0.0);
    }

    /** The winning intent, or ITEM when it is a request nothing else claimed, or null for ordinary chat. */
    public Intent primary() {
        Intent best = null;
        double bestScore = 0;
        for (Map.Entry<Intent, Double> e : scores.entrySet()) {
            if (e.getKey() == Intent.ITEM) continue;
            if (e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }
        double need = requestCue ? THRESHOLD_WITH_CUE : THRESHOLD_WITHOUT_CUE;
        if (best != null && bestScore >= need) return best;
        return requestCue ? Intent.ITEM : null;
    }

    public boolean is(Intent intent) {
        return primary() == intent;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Intent, Double> e : scores.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append('=').append(String.format("%.1f", e.getValue()));
        }
        return sb.length() == 0 ? "no keywords" : sb.toString();
    }
}
