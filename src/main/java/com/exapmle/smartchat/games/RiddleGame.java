package com.example.smartchat.games;

import com.example.smartchat.ai.Riddles;
import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Riddles: a round of several riddles at the difficulty you pick. Hints help but cost points,
 * a few wrong guesses reveal the answer, and streaks earn bonus points.
 */
public final class RiddleGame implements GameManager.Game {

    public static final Setting ROUNDS = Settings.integer("games", "riddle_rounds", 5, 1, 20,
            "How many riddles are in one riddle game.");

    public static final Setting WRONG_GUESSES = Settings.integer("games", "riddle_wrong_guesses", 3, 1, 10,
            "Wrong guesses allowed before the answer is revealed.");

    private static final Map<UUID, Set<String>> SEEN = new HashMap<>();

    private static final class State {
        boolean picking = true;
        Riddles.Tier tier = Riddles.Tier.EASY;
        Riddles.Riddle current;
        int hints;
        int wrong;
        int asked;
        int correct;
        int streak;
        int points;
    }

    public static void register() {
        GameManager.register(new RiddleGame());
    }

    @Override
    public String id() {
        return "riddle";
    }

    @Override
    public String name() {
        return "Riddles";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("riddle", "give me a riddle", "tell me a riddle", "riddle me this", "play riddles");
    }

    // ------------------------------------------------------------------ starting

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        State state = new State();
        session.state = state;
        Riddles.Tier tier = parseTier(ctx);
        if (tier != null) {
            state.tier = tier;
            state.picking = false;
            GameManager.say(ctx, "Riddle time! " + ROUNDS.asInt() + " riddles. Say 'hint' for a clue (costs points), "
                    + "'skip' to pass, or 'quit' to stop.");
            askNext(ctx, state);
        } else {
            GameManager.say(ctx, "Riddle time! Pick a difficulty: easy (" + Riddles.Tier.EASY.points + " pts), medium ("
                    + Riddles.Tier.MEDIUM.points + " pts), hard (" + Riddles.Tier.HARD.points + " pts), or 'random'.");
        }
        return true;
    }

    private static Riddles.Tier parseTier(ChatContext ctx) {
        for (String t : ctx.tokens) {
            Riddles.Tier tier = Riddles.tierByName(t);
            if (tier != null) return tier;
        }
        if (ctx.has("random")) return Riddles.Tier.values()[(int) (Math.random() * 3)];
        return null;
    }

    // ------------------------------------------------------------------ playing

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;

        if (state.picking) {
            Riddles.Tier tier = parseTier(ctx);
            if (tier == null) {
                if (ctx.tokens.size() <= 3) {
                    GameManager.say(ctx, "Pick easy, medium, hard or random.");
                    return true;
                }
                return false;
            }
            state.tier = tier;
            state.picking = false;
            askNext(ctx, state);
            return true;
        }

        if (ctx.hasAny("hint", "clue", "help me")) {
            if (state.hints >= 3) {
                GameManager.say(ctx, "No more hints! Take a guess, or say 'skip'.");
            } else {
                GameManager.say(ctx, "Hint " + (state.hints + 1) + ": " + Riddles.hint(state.current, state.hints));
                state.hints++;
            }
            return true;
        }
        if (ctx.hasAny("repeat", "say it again", "what was the riddle")) {
            GameManager.say(ctx, state.current.question());
            return true;
        }
        if (ctx.hasAny("skip", "pass", "next", "idk", "dont know")) {
            GameManager.say(ctx, "The answer was: " + Riddles.answerText(state.current) + ".");
            state.streak = 0;
            askNext(ctx, state);
            return true;
        }

        if (ctx.tokens.size() > 12) return false; // ordinary chat, not a guess

        if (Riddles.check(state.current, ctx.raw)) {
            int earned = Riddles.score(state.current, state.hints);
            state.streak++;
            int bonus = Math.min(20, 5 * (state.streak - 1));
            state.correct++;
            state.points += earned + bonus;
            GameManager.say(ctx, "Correct! +" + earned + " points" + (bonus > 0 ? " +" + bonus + " streak bonus" : "")
                    + (state.hints > 0 ? "  (" + state.hints + " hint" + (state.hints > 1 ? "s" : "") + " used)" : "") + ".");
            askNext(ctx, state);
        } else {
            state.wrong++;
            if (state.wrong >= WRONG_GUESSES.asInt()) {
                GameManager.say(ctx, "Not quite. The answer was: " + Riddles.answerText(state.current) + ".");
                state.streak = 0;
                askNext(ctx, state);
            } else {
                GameManager.say(ctx, "Not quite. Try again (" + (WRONG_GUESSES.asInt() - state.wrong)
                        + " guesses left), or say 'hint' or 'skip'.");
            }
        }
        return true;
    }

    private void askNext(ChatContext ctx, State state) {
        if (state.asked >= ROUNDS.asInt()) {
            finish(ctx, state);
            return;
        }
        Set<String> seen = SEEN.computeIfAbsent(ctx.player.getUUID(), k -> new HashSet<>());
        Riddles.Riddle riddle = Riddles.random(state.tier, seen);
        if (riddle == null) {
            finish(ctx, state);
            return;
        }
        seen.add(riddle.id());
        state.current = riddle;
        state.hints = 0;
        state.wrong = 0;
        state.asked++;
        GameManager.say(ctx, "Riddle " + state.asked + "/" + ROUNDS.asInt() + " ("
                + state.tier.name().toLowerCase(Locale.ROOT) + "): " + riddle.question());
    }

    // ------------------------------------------------------------------ the end

    private void finish(ChatContext ctx, State state) {
        ServerPlayer player = ctx.player;
        GameManager.addPoints(player, id(), state.points);
        GameManager.say(ctx, "Riddles finished! You solved " + state.correct + " of " + state.asked
                + " and earned " + state.points + " points.");
        if (state.correct * 2 > state.asked) {
            GameManager.addWin(player, id());
            GameManager.say(ctx, "Sharp mind! You get a prize for solving most of them.");
            GameManager.prize(ctx);
        }
        GameManager.say(ctx, "Say 'riddle' to play again.");
        GameManager.end(player, "finished");
    }
}
