package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.data.PlayerRecord;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.items.Delivery;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The framework every game plugs into. A game registers itself with GameManager.register(...);
 * the Listener starts games when someone says a trigger phrase, sends a player's messages to the game they are
 * playing, handles "quit" and timeouts, and keeps points, wins and the leaderboard.
 */
public final class GameManager {

    static {
        Settings.describeSection("games", "Games");
    }

    public static final Setting ENABLED = Settings.bool("games", "enabled", true,
            "Lets players play the chat games (riddles, rock paper scissors, trivia, tic-tac-toe...).");

    public static final Setting TIMEOUT_SECONDS = Settings.integer("games", "timeout_seconds", 120, 20, 3600,
            "A game ends by itself when the player has been silent this long.");

    public static final Setting MAX_START_WORDS = Settings.integer("games", "max_start_words", 10, 2, 40,
            "A game only starts from short messages (this many words or fewer).");

    public static final Setting PRIZES_ENABLED = Settings.bool("games", "prizes_enabled", true,
            "Winners get a small prize.");

    public static final Setting PRIZE_ITEMS = Settings.list("games", "prize_items", "emerald*2",
            "The prize for winning a game. Item ids, add *amount for more than one (for example emerald*3,golden_apple).");

    public static final Setting LEADERBOARD_SIZE = Settings.integer("games", "leaderboard_size", 10, 3, 25,
            "How many players the leaderboards show.");

    /** One game. Implementations live in their own files and register with GameManager.register(...). */
    public interface Game {
        String id();

        String name();

        /** Phrases (any wording, they get normalized) that start the game, like "rock paper scissors". */
        List<String> triggers();

        /** Called when the game starts. Return false if it could not start (the session is dropped). */
        boolean start(ChatContext ctx, Session session);

        /** Called for each message the player sends while playing. Return true when the message was used. */
        boolean onMessage(ChatContext ctx, Session session);

        /** Called when the game ends for any reason ("quit", "timeout", "finished"). */
        default void onEnd(ServerPlayer player, Session session, String reason) {}
    }

    /** What a player is currently playing. The game keeps its own data in state. */
    public static final class Session {
        public final UUID player;
        public final Game game;
        public Object state;
        public long lastActivity = System.currentTimeMillis();

        Session(UUID player, Game game) {
            this.player = player;
            this.game = game;
        }
    }

    public record Entry(String name, int points) {}

    private static final List<Game> GAMES = new ArrayList<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private GameManager() {}

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ registry and sessions

    public static void register(Game game) {
        GAMES.add(game);
    }

    public static List<Game> all() {
        return Collections.unmodifiableList(GAMES);
    }

    public static Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static boolean isPlaying(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Ends the player's current game (if any). */
    public static void end(ServerPlayer player, String reason) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) s.game.onEnd(player, s, reason);
    }

    public static void say(ChatContext ctx, String message) {
        Messages.say(ctx.player, message);
    }

    // ------------------------------------------------------------------ points and leaderboards

    public static void addPoints(ServerPlayer player, String gameId, int points) {
        if (points <= 0) return;
        PlayerRecord record = PlayerDataStore.get(player);
        record.scores.merge("pts_" + gameId, points, Integer::sum);
        PlayerDataStore.markDirty();
    }

    public static void addWin(ServerPlayer player, String gameId) {
        PlayerRecord record = PlayerDataStore.get(player);
        record.scores.merge("wins_" + gameId, 1, Integer::sum);
        PlayerDataStore.markDirty();
    }

    public static int points(ServerPlayer player, String gameId) {
        return PlayerDataStore.get(player).scores.getOrDefault("pts_" + gameId, 0);
    }

    public static int wins(ServerPlayer player, String gameId) {
        return PlayerDataStore.get(player).scores.getOrDefault("wins_" + gameId, 0);
    }

    /** Top players for one game, or for all games together when gameId is null. */
    public static List<Entry> leaderboard(String gameId, int limit) {
        List<Entry> out = new ArrayList<>();
        for (PlayerRecord record : PlayerDataStore.all().values()) {
            int total = 0;
            if (gameId != null) {
                total = record.scores.getOrDefault("pts_" + gameId, 0);
            } else {
                for (Map.Entry<String, Integer> e : record.scores.entrySet()) {
                    if (e.getKey().startsWith("pts_")) total += e.getValue();
                }
            }
            if (total > 0 && record.name != null && !record.name.isEmpty()) out.add(new Entry(record.name, total));
        }
        out.sort(Comparator.comparingInt(Entry::points).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Gives the winner's prize (the items from the prize_items setting). */
    public static void prize(ChatContext ctx) {
        if (!PRIZES_ENABLED.asBoolean()) return;
        StringBuilder given = new StringBuilder();
        for (String entry : PRIZE_ITEMS.asList()) {
            String id = entry;
            int count = 1;
            int star = entry.indexOf('*');
            if (star > 0) {
                id = entry.substring(0, star).trim();
                try {
                    count = Math.max(1, Integer.parseInt(entry.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            if (id.contains(":")) id = id.substring(id.indexOf(':') + 1);
            Item item = ItemIndex.byPath(id);
            if (item == null) continue;
            Delivery.toInventory(ctx.player, new ItemStack(item, count));
            if (given.length() > 0) given.append(", ");
            given.append(count).append("x ").append(Delivery.pretty(id));
        }
        if (given.length() > 0) say(ctx, "Prize: " + given + "!");
    }

    // ------------------------------------------------------------------ chat listener

    /** Register this at priority about 80 (after commands like teleport, before the small-talk handler). */
    public static final class Listener implements ChatRouter.Handler {

        @Override
        public boolean handle(ChatContext ctx) {
            if (!ENABLED.asBoolean()) return false;
            ServerPlayer player = ctx.player;
            UUID id = player.getUUID();
            long now = System.currentTimeMillis();

            Session current = SESSIONS.get(id);
            if (current != null && now - current.lastActivity > TIMEOUT_SECONDS.asInt() * 1000L) {
                end(player, "timeout");
                say(ctx, "Your " + current.game.name() + " game timed out. Say its name to play again!");
                current = null;
            }

            if (current != null) {
                if (isQuit(ctx)) {
                    end(player, "quit");
                    say(ctx, "Game ended. Say a game's name to play again, or 'what games' to see the list.");
                    return true;
                }
                current.lastActivity = now;
                return current.game.onMessage(ctx, current);
            }

            if (ctx.tokens.size() > MAX_START_WORDS.asInt()) return false;

            if (ctx.hasAny("play a game", "play games", "list games", "what games", "games list", "game list",
                    "what can i play", "any games")) {
                help(ctx);
                return true;
            }

            for (Game game : GAMES) {
                for (String trigger : game.triggers()) {
                    if (!ctx.has(TextUtil.normalize(trigger))) continue;
                    Session session = new Session(id, game);
                    SESSIONS.put(id, session);
                    boolean started = game.start(ctx, session);
                    if (!started) SESSIONS.remove(id);
                    return started;
                }
            }
            return false;
        }

        private static boolean isQuit(ChatContext ctx) {
            return ctx.hasAny("quit", "exit", "cancel", "give up", "end game", "stop playing", "stop the game",
                    "leave game") || (ctx.tokens.size() == 1 && ctx.has("stop"));
        }

        private static void help(ChatContext ctx) {
            if (GAMES.isEmpty()) {
                say(ctx, "No games are available right now.");
                return;
            }
            say(ctx, "Games you can play (say the name to start, 'quit' to stop):");
            for (Game game : GAMES) {
                String example = game.triggers().isEmpty() ? game.name().toLowerCase() : game.triggers().get(0);
                say(ctx, "  " + game.name() + "  -  say \"" + example + "\"");
            }
        }
    }
}
