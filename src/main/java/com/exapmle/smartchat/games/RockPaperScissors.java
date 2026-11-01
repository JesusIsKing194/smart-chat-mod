package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Rock Paper Scissors, first to a few round wins. The assistant remembers what each player tends to throw
 * (their favorite move, and what they throw after each move) and plays to beat it.
 */
public final class RockPaperScissors implements GameManager.Game {

    public static final Setting DIFFICULTY = Settings.integer("games", "rps_difficulty", 2, 0, 2,
            "How clever the assistant is at rock paper scissors: 0 = random, 1 = learns your favorite move, "
                    + "2 = learns your patterns (hardest).");

    public static final Setting WINS_NEEDED = Settings.integer("games", "rps_wins_needed", 3, 1, 10,
            "Round wins needed to win a match (3 = best of five).");

    private static final String[] NAMES = {"Rock", "Paper", "Scissors"};
    private static final Random R = new Random();

    // what the assistant has learned about each player (kept while the server runs)
    private static final Map<UUID, int[]> FREQ = new HashMap<>();
    private static final Map<UUID, int[][]> TRANS = new HashMap<>();
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private static final Map<UUID, Integer> STREAK = new HashMap<>();

    private static final class State {
        int player;
        int ai;
        int round = 1;
    }

    public static void register() {
        GameManager.register(new RockPaperScissors());
    }

    @Override
    public String id() {
        return "rps";
    }

    @Override
    public String name() {
        return "Rock Paper Scissors";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("rock paper scissors", "rps", "play rock paper scissors");
    }

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        session.state = new State();
        GameManager.say(ctx, "Rock Paper Scissors! First to " + WINS_NEEDED.asInt()
                + " round wins takes the match. Say rock, paper or scissors. (Say 'quit' to stop.)");
        return true;
    }

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;
        int move = parseMove(ctx);
        if (move < 0) {
            if (ctx.tokens.size() <= 3) {
                GameManager.say(ctx, "Say rock, paper or scissors!");
                return true;
            }
            return false; // ordinary chat, not a move
        }

        ServerPlayer player = ctx.player;
        UUID id = player.getUUID();
        int ai = aiMove(id);
        learn(id, move);

        String line = "Round " + state.round + ": you " + NAMES[move] + " vs me " + NAMES[ai] + ". ";
        if (move == ai) {
            line += "A tie!";
        } else if (beats(move, ai)) {
            state.player++;
            line += "You win the round!";
        } else {
            state.ai++;
            line += "I win the round!";
        }
        state.round++;
        GameManager.say(ctx, line + "  (You " + state.player + " - " + state.ai + " Me)");

        int needed = WINS_NEEDED.asInt();
        if (state.player >= needed || state.ai >= needed) {
            finish(ctx, state, id);
        } else {
            GameManager.say(ctx, "Your move!");
        }
        return true;
    }

    // ------------------------------------------------------------------ rules and the learning

    private static boolean beats(int a, int b) {
        return (a - b + 3) % 3 == 1; // paper beats rock, scissors beats paper, rock beats scissors
    }

    private static int parseMove(ChatContext ctx) {
        for (String t : ctx.tokens) {
            switch (t) {
                case "rock": case "stone": return 0;
                case "paper": return 1;
                case "scissors": case "scissor": return 2;
                default: break;
            }
        }
        if (ctx.tokens.size() == 1) {
            switch (ctx.tokens.get(0)) {
                case "r": return 0;
                case "p": return 1;
                case "s": return 2;
                default: break;
            }
        }
        return -1;
    }

    private static int argmax(int[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    /** The assistant's throw: it predicts what the player will throw and plays the move that beats it. */
    private static int aiMove(UUID id) {
        int difficulty = DIFFICULTY.asInt();
        if (difficulty == 0 || R.nextInt(100) < 12) return R.nextInt(3); // a little randomness so it is not unbeatable

        int predicted = -1;
        if (difficulty >= 2) {
            Integer last = LAST.get(id);
            int[][] trans = TRANS.get(id);
            if (last != null && trans != null) {
                int[] row = trans[last];
                if (row[0] + row[1] + row[2] >= 3) predicted = argmax(row);
            }
        }
        if (predicted < 0) {
            int[] freq = FREQ.get(id);
            if (freq != null && freq[0] + freq[1] + freq[2] >= 3) predicted = argmax(freq);
        }
        if (predicted < 0) return R.nextInt(3);
        return (predicted + 1) % 3;
    }

    private static void learn(UUID id, int move) {
        FREQ.computeIfAbsent(id, k -> new int[3])[move]++;
        int[][] trans = TRANS.computeIfAbsent(id, k -> new int[3][3]);
        Integer last = LAST.get(id);
        if (last != null) trans[last][move]++;
        LAST.put(id, move);
    }

    // ------------------------------------------------------------------ the end of a match

    private void finish(ChatContext ctx, State state, UUID id) {
        ServerPlayer player = ctx.player;
        boolean won = state.player > state.ai;
        if (won) {
            int streak = STREAK.merge(id, 1, Integer::sum);
            int points = 30 + 5 * (streak - 1);
            GameManager.addPoints(player, id(), points);
            GameManager.addWin(player, id());
            GameManager.say(ctx, "You win the match " + state.player + " - " + state.ai + "!  +" + points + " points"
                    + (streak > 1 ? "  (win streak: " + streak + ")" : ""));
            GameManager.prize(ctx);
            ctx.level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 2, player.getZ(), 40, 0.8, 0.0, 0.8, 0.15);
            ctx.level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        } else {
            STREAK.remove(id);
            GameManager.addPoints(player, id(), 5);
            GameManager.say(ctx, "I win the match " + state.ai + " - " + state.player + "! +5 points for playing. "
                    + "I'm learning how you play...");
        }
        GameManager.say(ctx, "Say 'rock paper scissors' to play again.");
        GameManager.end(player, "finished");
    }
}
