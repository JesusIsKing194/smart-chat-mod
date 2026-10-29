package com.example.smartchat.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Everything the mod remembers about one player (saved in config/smartchat/players.json). */
public final class PlayerRecord {

    public String name = "";
    /** Easter eggs this player has discovered. */
    public Set<String> eggs = new HashSet<>();
    /** Best scores per game id. */
    public Map<String, Integer> scores = new HashMap<>();
    /** Story id -> set of ending ids this player has found. */
    public Map<String, Set<String>> endings = new HashMap<>();
    /** When true, other players cannot trap this player. */
    public boolean trapOptOut = false;
    /** When true, the assistant stays quiet for this player (actions still work). */
    public boolean quietAi = false;

    /** Repairs null fields after loading old or hand-edited files. */
    void fix() {
        if (name == null) name = "";
        if (eggs == null) eggs = new HashSet<>();
        if (scores == null) scores = new HashMap<>();
        if (endings == null) endings = new HashMap<>();
    }
}
