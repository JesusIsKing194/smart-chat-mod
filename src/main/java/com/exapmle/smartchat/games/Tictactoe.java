package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.util.NumberWords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tic-Tac-Toe on the ground in front of you: a board of colored concrete (red X, blue O) that you play by saying
 * a number from 1 to 9. The ground is put back when the game ends.
 */
public final class TicTacToe implements GameManager.Game {

    public static final Setting DIFFICULTY = Settings.integer("games", "ttt_difficulty", 1, 0, 2,
            "How good the assistant is at tic-tac-toe: 0 = random, 1 = blocks and takes wins but can slip up, "
                    + "2 = perfect (you can only draw).");

    private static final Random R = new Random();
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private static final class State {
        char[] board = new char[9];
        BlockPos[] cells = new BlockPos[9];
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        ServerLevel level;
        BlockState white;
        BlockState red;
        BlockState blue;
        BlockState gray;
    }

    public static void register() {
        GameManager.register(new TicTacToe());
    }

    @Override
    public String id() {
        return "tictactoe";
    }

    @Override
    public String name() {
        return "Tic-Tac-Toe";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("tic tac toe", "tictactoe", "noughts and crosses");
    }

    private static BlockState concrete(String color) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color + "_concrete")).defaultBlockState();
    }

    // ------------------------------------------------------------------ starting

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        State state = new State();
        state.level = ctx.level;
        Arrays.fill(state.board, ' ');
        state.white = concrete("white");
        state.red = concrete("red");
        state.blue = concrete("blue");
        state.gray = concrete("gray");

        ServerPlayer p = ctx.player;
        Direction f = p.getDirection();
        Direction rg = f.getClockWise();
        BlockPos feet = p.blockPosition();
        int floorY = feet.getY() - 1;
        int cx = feet.getX() + f.getStepX() * 5;
        int cz = feet.getZ() + f.getStepZ() * 5;

        for (int df = -2; df <= 2; df++) {
            for (int dr = -2; dr <= 2; dr++) {
                BlockPos pos = new BlockPos(cx + f.getStepX() * df + rg.getStepX() * dr, floorY,
                        cz + f.getStepZ() * df + rg.getStepZ() * dr);
                BlockState st = state.level.hasChunkAt(pos) ? state.level.getBlockState(pos) : null;
                if (st == null || st.isAir() || !st.getFluidState().isEmpty() || state.level.getBlockEntity(pos) != null
                        || st.getCollisionShape(state.level, pos).isEmpty()) {
                    GameManager.say(ctx, "I need a flat, solid patch of ground in front of you for the board. Try somewhere open!");
                    return false;
                }
                state.originals.put(pos, st);
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int df = (1 - r) * 2; // row 0 is the far row, like on paper
                int dr = (c - 1) * 2;
                state.cells[r * 3 + c] = new BlockPos(cx + f.getStepX() * df + rg.getStepX() * dr, floorY,
                        cz + f.getStepZ() * df + rg.getStepZ() * dr);
            }
        }
        session.state = state;
        paint(state);

        GameManager.say(ctx, "Tic-Tac-Toe! The board is on the ground in front of you. You are X (red), I'm O (blue). "
                + "Say a number from 1 to 9 to place your X.");
        showBoard(ctx, state);
        return true;
    }

    // ------------------------------------------------------------------ playing

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;
        int cell = parseCell(ctx);
        if (cell < 0) {
            if (ctx.tokens.size() <= 3) {
                GameManager.say(ctx, "Say a number from 1 to 9.");
                return true;
            }
            return false; // ordinary chat
        }
        if (state.board[cell] != ' ') {
            GameManager.say(ctx, "That square is taken. Pick another number.");
            return true;
        }

        place(state, cell, 'X');
        if (finishIfOver(ctx, state)) return true;

        int reply = aiMove(state);
        place(state, reply, 'O');
        GameManager.say(ctx, "I play " + (reply + 1) + ".");
        if (finishIfOver(ctx, state)) return true;

        showBoard(ctx, state);
        return true;
    }

    @Override
    public void onEnd(ServerPlayer player, GameManager.Session session, String reason) {
        State state = (State) session.state;
        if (state == null) return;
        for (Map.Entry<BlockPos, BlockState> e : state.originals.entrySet()) {
            if (state.level.hasChunkAt(e.getKey())) state.level.setBlock(e.getKey(), e.getValue(), 3);
        }
        state.originals.clear();
    }

    private static int parseCell(ChatContext ctx) {
        for (String t : ctx.tokens) {
            Integer n = NumberWords.parse(t);
            if (n != null && n >= 1 && n <= 9) return n - 1;
        }
        return -1;
    }

    private static void place(State state, int cell, char mark) {
        state.board[cell] = mark;
        state.level.setBlock(state.cells[cell], mark == 'X' ? state.red : state.blue, 3);
        state.level.playSound(null, state.cells[cell], SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS,
                1.0f, mark == 'X' ? 1.4f : 0.8f);
    }

    private static void paint(State state) {
        for (BlockPos pos : state.originals.keySet()) state.level.setBlock(pos, state.gray, 3);
        for (int i = 0; i < 9; i++) state.level.setBlock(state.cells[i], state.white, 3);
    }

    private static void showBoard(ChatContext ctx, State state) {
        for (int r = 0; r < 3; r++) {
            StringBuilder line = new StringBuilder("   ");
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                line.append(state.board[i] == ' ' ? String.valueOf(i + 1) : String.valueOf(state.board[i]));
                if (c < 2) line.append("  |  ");
            }
            GameManager.say(ctx, line.toString());
        }
        GameManager.say(ctx, "Your move!");
    }

    // ------------------------------------------------------------------ rules and the assistant

    private static char winner(char[] b) {
        for (int[] line : LINES) {
            if (b[line[0]] != ' ' && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) return b[line[0]];
        }
        return ' ';
    }

    private static boolean full(char[] b) {
        for (char c : b) {
            if (c == ' ') return false;
        }
        return true;
    }

    private static int winningCell(char[] b, char mark) {
        for (int i = 0; i < 9; i++) {
            if (b[i] != ' ') continue;
            b[i] = mark;
            boolean wins = winner(b) == mark;
            b[i] = ' ';
            if (wins) return i;
        }
        return -1;
    }

    private static int minimax(char[] b, boolean aiTurn) {
        char w = winner(b);
        if (w == 'O') return 1;
        if (w == 'X') return -1;
        if (full(b)) return 0;
        int best = aiTurn ? -2 : 2;
        for (int i = 0; i < 9; i++) {
            if (b[i] != ' ') continue;
            b[i] = aiTurn ? 'O' : 'X';
            int v = minimax(b, !aiTurn);
            b[i] = ' ';
            best = aiTurn ? Math.max(best, v) : Math.min(best, v);
        }
        return best;
    }

    private int aiMove(State state) {
        char[] b = state.board;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (b[i] == ' ') free.add(i);
        }
        int difficulty = DIFFICULTY.asInt();
        if (difficulty == 0 || (difficulty == 1 && R.nextInt(100) < 20)) return free.get(R.nextInt(free.size()));

        if (difficulty >= 2) {
            int bestValue = -2;
            List<Integer> bestCells = new ArrayList<>();
            for (int i : free) {
                b[i] = 'O';
                int v = minimax(b, false);
                b[i] = ' ';
                if (v > bestValue) {
                    bestValue = v;
                    bestCells.clear();
                }
                if (v == bestValue) bestCells.add(i);
            }
            return bestCells.get(R.nextInt(bestCells.size()));
        }

        int win = winningCell(b, 'O');
        if (win >= 0) return win;
        int block = winningCell(b, 'X');
        if (block >= 0) return block;
        if (b[4] == ' ') return 4;
        int[] corners = {0, 2, 6, 8};
        List<Integer> openCorners = new ArrayList<>();
        for (int c : corners) {
            if (b[c] == ' ') openCorners.add(c);
        }
        if (!openCorners.isEmpty()) return openCorners.get(R.nextInt(openCorners.size()));
        return free.get(R.nextInt(free.size()));
    }

    // ------------------------------------------------------------------ the end

    private boolean finishIfOver(ChatContext ctx, State state) {
        char w = winner(state.board);
        if (w == ' ' && !full(state.board)) return false;
        ServerPlayer player = ctx.player;
        if (w == 'X') {
            GameManager.addPoints(player, id(), 30);
            GameManager.addWin(player, id());
            GameManager.say(ctx, "Three in a row! You win! +30 points.");
            GameManager.prize(ctx);
        } else if (w == 'O') {
            GameManager.addPoints(player, id(), 3);
            GameManager.say(ctx, "Three in a row for me! I win this one. +3 points for playing.");
        } else {
            GameManager.addPoints(player, id(), 10);
            GameManager.say(ctx, "A draw! +10 points.");
        }
        GameManager.say(ctx, "Say 'tic tac toe' to play again. The ground goes back to normal now.");
        GameManager.end(player, "finished");
        return true;
    }
}
