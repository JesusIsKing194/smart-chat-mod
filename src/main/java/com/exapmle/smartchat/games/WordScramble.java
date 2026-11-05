package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.util.Scheduler;
import com.example.smartchat.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Word Scramble: unscramble Minecraft words against a timer. Words get harder as the round goes on,
 * quick answers earn a time bonus and hints cost points.
 */
public final class WordScramble implements GameManager.Game {

    public static final Setting WORDS = Settings.integer("games", "scramble_words", 6, 3, 12,
            "How many words are in one round (easy ones first, then medium, then hard).");

    public static final Setting SECONDS = Settings.integer("games", "scramble_seconds", 30, 10, 120,
            "Seconds you get for each word.");

    private static final Random R = new Random();

    private static final String[] EASY = {"sword", "torch", "apple", "arrow", "bread", "chest", "stone", "glass", "wheat",
            "spider", "zombie", "bucket", "hopper", "piston", "anvil", "sheep", "slime", "lever", "bone", "dirt", "gold",
            "iron", "coal", "sand", "bow", "egg"};
    private static final String[] MEDIUM = {"creeper", "pickaxe", "diamond", "emerald", "skeleton", "villager", "obsidian",
            "furnace", "lantern", "bedrock", "netherite", "redstone", "compass", "dispenser", "enderman", "pumpkin",
            "observer", "trapdoor", "saddle", "potion", "shovel", "ghast", "blaze", "wither"};
    private static final String[] HARD = {"cobblestone", "netherrack", "ender dragon", "iron golem", "crafting table",
            "enchanting table", "golden apple", "nether portal", "pressure plate", "diamond sword", "glowstone dust",
            "ancient debris", "brewing stand", "redstone torch", "soul sand", "totem of undying", "bone meal"};

    private static final class State {
        List<String> words = new ArrayList<>();
        int index = -1;
        String answer = "";
        int hints;
        long askedAt;
        int correct;
        int points;
        Scheduler.Handle timer;
        ChatContext ctx;
    }

    public static void register() {
        GameManager.register(new WordScramble());
    }

    @Override
    public String id() {
        return "scramble";
    }

    @Override
    public String name() {
        return "Word Scramble";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("word scramble", "scramble", "unscramble", "word game");
    }

    // ------------------------------------------------------------------ playing

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        State state = new State();
        state.ctx = ctx;
        int n = WORDS.asInt();
        for (int i = 0; i < n; i++) {
            String[] pool = i < n / 3 ? EASY : i < 2 * n / 3 ? MEDIUM : HARD;
            String word;
            int guard = 0;
            do {
                word = pool[R.nextInt(pool.length)];
            } while (state.words.contains(word) && ++guard < 20);
            state.words.add(word);
        }
        session.state = state;
        GameManager.say(ctx, "Word Scramble! " + n + " Minecraft words, " + SECONDS.asInt() + " seconds each. "
                + "Say 'hint' for the first letter (costs points) or 'skip'.");
        next(ctx, state, session);
        return true;
    }

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;

        if (ctx.hasAny("hint", "clue")) {
            state.hints++;
            GameManager.say(ctx, "Hint: it starts with '" + state.answer.charAt(0) + "' and has "
                    + state.answer.replace(" ", "").length() + " letters.");
            return true;
        }
        if (ctx.hasAny("skip", "pass", "next", "idk")) {
            GameManager.say(ctx, "It was: " + state.answer + ".");
            next(ctx, state, session);
            return true;
        }
        if (ctx.tokens.size() > 4) return false; // ordinary chat

        if (TextUtil.containsPhrase(ctx.text, TextUtil.normalize(state.answer))) {
            long elapsed = (System.currentTimeMillis() - state.askedAt) / 1000L;
            int bonus = (int) Math.max(0, (SECONDS.asInt() - elapsed) / 3);
            int earned = Math.max(1, 10 + bonus - 3 * state.hints);
            state.correct++;
            state.points += earned;
            GameManager.say(ctx, "Correct! +" + earned + " points" + (bonus > 0 ? " (speed bonus " + bonus + ")" : "") + ".");
            next(ctx, state, session);
        } else {
            GameManager.say(ctx, "Not that one. Keep trying!");
        }
        return true;
    }

    @Override
    public void onEnd(ServerPlayer player, GameManager.Session session, String reason) {
        State state = (State) session.state;
        if (state != null && state.timer != null) state.timer.cancel();
    }

    // ------------------------------------------------------------------ asking

    private void next(ChatContext ctx, State state, GameManager.Session session) {
        if (state.timer != null) state.timer.cancel();
        state.index++;
        if (state.index >= state.words.size()) {
            finish(ctx, state);
            return;
        }
        state.answer = state.words.get(state.index);
        state.hints = 0;
        state.askedAt = System.currentTimeMillis();
        GameManager.say(ctx, "Word " + (state.index + 1) + "/" + state.words.size() + ":  " + scramble(state.answer));

        final int asked = state.index;
        state.timer = Scheduler.later(SECONDS.asInt() * 20, () -> {
            if (GameManager.session(ctx.player) != session || state.index != asked) return;
            session.lastActivity = System.currentTimeMillis();
            GameManager.say(ctx, "Time's up! It was: " + state.answer + ".");
            next(ctx, state, session);
        });
    }

    /** Shuffles the letters of each word (never returning the original order) and spaces them out. */
    private static String scramble(String phrase) {
        StringBuilder out = new StringBuilder();
        for (String word : phrase.split(" ")) {
            List<Character> letters = new ArrayList<>();
            for (char c : word.toCharArray()) letters.add(c);
            String shuffled = word;
            for (int attempt = 0; attempt < 12 && shuffled.equals(word) && word.length() > 1; attempt++) {
                Collections.shuffle(letters, R);
                StringBuilder sb = new StringBuilder();
                for (char c : letters) sb.append(c);
                shuffled = sb.toString();
            }
            if (out.length() > 0) out.append("   /   ");
            for (int i = 0; i < shuffled.length(); i++) {
                if (i > 0) out.append(' ');
                out.append(Character.toUpperCase(shuffled.charAt(i)));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ the end

    private void finish(ChatContext ctx, State state) {
        ServerPlayer player = ctx.player;
        GameManager.addPoints(player, id(), state.points);
        GameManager.say(ctx, "Word Scramble finished! You unscrambled " + state.correct + " of " + state.words.size()
                + " and earned " + state.points + " points.");
        if (state.correct * 3 >= state.words.size() * 2) {
            GameManager.addWin(player, id());
            GameManager.say(ctx, "Sharp eyes! You get a prize.");
            GameManager.prize(ctx);
        }
        GameManager.say(ctx, "Say 'word scramble' to play again.");
        GameManager.end(player, "finished");
    }
}
