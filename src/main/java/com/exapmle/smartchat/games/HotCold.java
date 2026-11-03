package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Hot and Cold: a secret spot is hidden somewhere near you. Walk around and watch the action bar,
 * it tells you warmer or colder. Stand on the spot to win.
 */
public final class HotCold implements GameManager.Game {

    public static final Setting RADIUS = Settings.integer("games", "hotcold_radius", 50, 20, 300,
            "The hidden spot is at most this many blocks away.");

    public static final Setting MINUTES = Settings.integer("games", "hotcold_minutes", 3, 1, 10,
            "How long the hunt lasts.");

    private static final Random R = new Random();

    private static final class State {
        double targetX;
        double targetZ;
        double lastDistance = -1;
        Scheduler.Handle handle;
        ChatContext ctx;
    }

    public static void register() {
        GameManager.register(new HotCold());
    }

    @Override
    public String id() {
        return "hotcold";
    }

    @Override
    public String name() {
        return "Hot and Cold";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("hot and cold", "hot or cold", "hot cold", "treasure hunt");
    }

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        ServerPlayer p = ctx.player;
        State state = new State();
        state.ctx = ctx;
        double angle = R.nextDouble() * Math.PI * 2;
        double distance = 20 + R.nextDouble() * Math.max(1, RADIUS.asInt() - 20);
        state.targetX = p.getX() + Math.cos(angle) * distance;
        state.targetZ = p.getZ() + Math.sin(angle) * distance;
        session.state = state;

        GameManager.say(ctx, "Hot and Cold! A secret spot is hidden within " + RADIUS.asInt() + " blocks. Watch the "
                + "action bar: it says warmer or colder. Stand on the spot to win! Say 'how close' for a hint.");

        int seconds = MINUTES.asInt() * 60;
        state.handle = Scheduler.repeat(20, 20, seconds, step -> {
            session.lastActivity = System.currentTimeMillis(); // walking counts as playing
            if (GameManager.session(p) != session) {
                if (state.handle != null) state.handle.cancel();
                return;
            }
            double d = distance(p, state);
            if (d <= 2.5) {
                win(state, session);
                return;
            }
            String trend = state.lastDistance < 0 ? "" : d < state.lastDistance - 0.5 ? "Warmer" : d > state.lastDistance + 0.5 ? "Colder" : "Same";
            Messages.actionBar(p, (trend.isEmpty() ? "" : trend + " - ") + temperature(d));
            state.lastDistance = d;
            if (step == seconds - 1) timeout(state, session);
        });
        return true;
    }

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;
        if (ctx.hasAny("how close", "hint", "hot or cold", "where", "am i close", "temperature")) {
            GameManager.say(ctx, "You're " + temperature(distance(ctx.player, state)).toLowerCase() + ".");
            return true;
        }
        return false; // ordinary chat
    }

    @Override
    public void onEnd(ServerPlayer player, GameManager.Session session, String reason) {
        State state = (State) session.state;
        if (state == null) return;
        if (state.handle != null) state.handle.cancel();
        if (reason.equals("quit") || reason.equals("timeout")) {
            Messages.say(player, "The secret spot was around " + (int) state.targetX + ", " + (int) state.targetZ + ".");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static double distance(ServerPlayer p, State state) {
        double dx = p.getX() - state.targetX;
        double dz = p.getZ() - state.targetZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String temperature(double d) {
        if (d > 40) return "Freezing";
        if (d > 25) return "Cold";
        if (d > 15) return "Cool";
        if (d > 8) return "Warm";
        if (d > 4) return "Hot";
        return "Burning hot";
    }

    private void win(State state, GameManager.Session session) {
        ChatContext ctx = state.ctx;
        ServerPlayer p = ctx.player;
        GameManager.addPoints(p, id(), 40);
        GameManager.addWin(p, id());
        GameManager.say(ctx, "You found it! +40 points.");
        GameManager.prize(ctx);
        ctx.level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 40, 0.8, 0.5, 0.8, 0.15);
        ctx.level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        GameManager.say(ctx, "Say 'hot and cold' to hunt again.");
        GameManager.end(p, "finished");
    }

    private void timeout(State state, GameManager.Session session) {
        ChatContext ctx = state.ctx;
        GameManager.addPoints(ctx.player, id(), 3);
        GameManager.say(ctx, "Time's up! The spot was around " + (int) state.targetX + ", " + (int) state.targetZ
                + ". +3 points for trying.");
        GameManager.end(ctx.player, "finished");
    }
}
