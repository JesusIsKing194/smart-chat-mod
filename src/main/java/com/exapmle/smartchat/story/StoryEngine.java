package com.example.smartchat.story;

import com.example.smartchat.SmartChatSpawnerMod;
import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.data.PlayerRecord;
import com.example.smartchat.eggs.Egg.Tools;
import com.example.smartchat.games.GameManager;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Scary choose-your-own-ending campfire stories. Everyone within the radius of the storyteller sits around a campfire,
 * hears the story line by line, and votes A, B or C at each of the three decision points (majority wins, a tie goes to
 * the storyteller). Each story has 27 endings, and every ending a player reaches is remembered.
 * Story files live in src/main/resources/data/smartchatmod/stories/.
 */
public final class StoryEngine implements ChatRouter.Handler {

    static {
        Settings.describeSection("story", "Campfire stories");
    }

    public static final Setting ENABLED = Settings.bool("story", "enabled", true,
            "Lets players start scary campfire stories (\"campfire story\").");

    public static final Setting RADIUS = Settings.integer("story", "radius", 15, 3, 40,
            "Players within this many blocks of the storyteller join the story.");

    public static final Setting VOTE_SECONDS = Settings.integer("story", "vote_seconds", 30, 10, 120,
            "Seconds the group has to vote at each decision (silence lets the storyteller's choice, or luck, decide).");

    public static final Setting NIGHT = Settings.bool("story", "turn_to_night", true,
            "The world turns to night during a story (for everyone in that world) and the time is restored afterwards.");

    public static final Setting COOLDOWN_SECONDS = Settings.integer("story", "cooldown_seconds", 120, 0, 3600,
            "Seconds a player must wait before starting another story.");

    public static final Setting LINE_PAUSE_TICKS = Settings.integer("story", "line_pause_ticks", 40, 10, 120,
            "Pause between lines of the story (20 ticks = 1 second).");

    /** The 20 story files, without ".json". */
    private static final String[] STORY_IDS = {"lantern", "knock", "nodoors", "lighthouse", "footsteps", "humming",
            "villager", "embers", "mansion", "fog", "chest", "temple", "sleepover", "fisherman", "minecart", "garden",
            "choir", "signs", "torches", "keeper"};

    private static final String[] TRIGGERS = {"campfire story", "campfire stories", "scary story", "scary stories",
            "ghost story", "spooky story", "horror story", "scary campfire"};

    public static final int ENDINGS_PER_STORY = 27;

    private record Node(String text, String a, String b, String c, boolean end) {}

    private record Story(String id, String title, Map<String, Node> nodes) {}

    private static final class Session {
        Story story;
        UUID starter;
        List<UUID> players = new ArrayList<>();
        Map<UUID, String> votes = new HashMap<>();
        String node = "r";
        boolean open;
        boolean over;
        CampfireScene scene;
        Scheduler.Handle voteTimer;
        MinecraftServer server;
    }

    private static final Random R = new Random();
    private static final Map<String, Story> STORIES = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Long> LAST_START = new HashMap<>();
    private static final Map<UUID, String> LAST_STORY = new HashMap<>();

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    public static int storyCount() {
        loadAll();
        return STORIES.size();
    }

    public static String titleOf(String storyId) {
        loadAll();
        Story s = STORIES.get(storyId);
        return s == null ? storyId : s.title();
    }

    // ------------------------------------------------------------------ loading

    private static void loadAll() {
        if (loaded) return;
        loaded = true;
        for (String id : STORY_IDS) {
            Story story = load(id);
            if (story != null) STORIES.put(id, story);
        }
        SmartChatSpawnerMod.LOGGER.info("Loaded {} campfire stories.", STORIES.size());
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static Story load(String id) {
        try (InputStream in = StoryEngine.class.getResourceAsStream("/data/smartchatmod/stories/" + id + ".json")) {
            if (in == null) {
                SmartChatSpawnerMod.LOGGER.warn("Story file missing: {}", id);
                return null;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Node> nodes = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("nodes").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                boolean end = o.has("end") && o.get("end").getAsBoolean();
                nodes.put(e.getKey(), new Node(str(o, "t"), str(o, "a"), str(o, "b"), str(o, "c"), end));
            }
            return new Story(id, str(root, "title"), nodes);
        } catch (Exception ex) {
            SmartChatSpawnerMod.LOGGER.error("Could not load story {}", id, ex);
            return null;
        }
    }

    // ------------------------------------------------------------------ chat

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ENABLED.asBoolean()) return false;
        Session session = BY_PLAYER.get(ctx.player.getUUID());
        if (session != null) return inStory(ctx, session);
        for (String trigger : TRIGGERS) {
            if (ctx.has(trigger) && ctx.tokens.size() <= 8) return start(ctx);
        }
        return false;
    }

    private boolean inStory(ChatContext ctx, Session s) {
        UUID id = ctx.player.getUUID();
        if (ctx.hasAny("stop story", "end story", "quit story", "leave story", "stop the story")) {
            if (id.equals(s.starter)) {
                say(s, "The storyteller ends the tale... the fire dies down.", ChatFormatting.RED);
                finish(s, 0);
            } else {
                s.players.remove(id);
                BY_PLAYER.remove(id);
                Messages.say(ctx.player, "You leave the circle.");
            }
            return true;
        }
        String letter = null;
        if (ctx.tokens.size() <= 2 && !ctx.tokens.isEmpty()) {
            String last = ctx.tokens.get(ctx.tokens.size() - 1);
            if (last.equals("a") || last.equals("b") || last.equals("c")) letter = last;
        }
        if (letter == null) return false; // ordinary chat
        if (!s.open) {
            Messages.actionBar(ctx.player, "Wait for the options...");
            return true;
        }
        s.votes.put(id, letter);
        Messages.actionBar(ctx.player, "You voted " + letter.toUpperCase());
        if (s.votes.size() >= s.players.size()) resolve(s);
        return true;
    }

    // ------------------------------------------------------------------ starting

    private boolean start(ChatContext ctx) {
        loadAll();
        ServerPlayer starter = ctx.player;
        if (STORIES.isEmpty()) {
            ctx.reply("The story book is empty right now. (The story files are missing.)");
            return true;
        }
        if (GameManager.isPlaying(starter)) {
            ctx.reply("Finish your game first (say 'quit'), then come to the campfire.");
            return true;
        }
        long now = System.currentTimeMillis();
        int cooldown = COOLDOWN_SECONDS.asInt();
        Long last = LAST_START.get(starter.getUUID());
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            Messages.actionBar(starter, "The fire needs a moment... " + ((cooldown * 1000L - (now - last)) / 1000L + 1) + "s");
            return true;
        }

        List<ServerPlayer> group = new ArrayList<>();
        for (ServerPlayer p : Tools.nearby(ctx, RADIUS.asInt())) {
            if (!BY_PLAYER.containsKey(p.getUUID()) && !GameManager.isPlaying(p)) group.add(p);
        }
        if (!group.contains(starter)) group.add(0, starter);

        CampfireScene scene = CampfireScene.build(ctx.level, starter, group, NIGHT.asBoolean());
        if (scene == null) {
            ctx.reply("I can't find a good spot for a campfire here. Try a flatter, more open place!");
            return true;
        }

        Session s = new Session();
        s.story = pickStory(starter);
        s.starter = starter.getUUID();
        s.scene = scene;
        s.server = ctx.level.getServer();
        for (ServerPlayer p : group) {
            s.players.add(p.getUUID());
            BY_PLAYER.put(p.getUUID(), s);
        }
        LAST_START.put(starter.getUUID(), now);
        LAST_STORY.put(starter.getUUID(), s.story.id());

        say(s, "The fire crackles. Everyone sits down... " + starter.getName().getString() + " begins to tell a story.", ChatFormatting.GOLD);
        say(s, "At each choice, type A, B or C. The group votes. (Say 'stop story' to end it.)", ChatFormatting.GRAY);
        Scheduler.later(80, () -> {
            if (s.over) return;
            say(s, "\"" + s.story.title() + "\"", ChatFormatting.YELLOW);
            play(s, "r");
        });
        return true;
    }

    /** Prefers a story the storyteller has found the fewest endings in, and not the one they just heard. */
    private static Story pickStory(ServerPlayer starter) {
        PlayerRecord record = PlayerDataStore.get(starter);
        int fewest = Integer.MAX_VALUE;
        List<Story> best = new ArrayList<>();
        for (Story story : STORIES.values()) {
            if (story.id().equals(LAST_STORY.get(starter.getUUID())) && STORIES.size() > 1) continue;
            Set<String> found = record.endings.get(story.id());
            int n = found == null ? 0 : found.size();
            if (n < fewest) {
                fewest = n;
                best.clear();
            }
            if (n == fewest) best.add(story);
        }
        return best.get(R.nextInt(best.size()));
    }

    // ------------------------------------------------------------------ telling

    private void play(Session s, String nodeId) {
        Node node = s.story.nodes().get(nodeId);
        if (node == null) {
            say(s, "...and the story fades into the dark. (A page is missing from the book.)", ChatFormatting.RED);
            finish(s, 60);
            return;
        }
        s.node = nodeId;
        s.votes.clear();
        s.open = false;

        String[] lines = node.text().split("(?<=[.!?])\\s+");
        int pause = LINE_PAUSE_TICKS.asInt();
        int delay = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            Scheduler.later(delay, () -> {
                if (!s.over) say(s, line, ChatFormatting.GOLD);
            });
            delay += pause;
        }

        if (node.end()) {
            Scheduler.later(delay, () -> {
                if (s.over) return;
                say(s, "~ THE END ~", ChatFormatting.RED);
                recordEnding(s, nodeId);
            });
            finishLater(s, delay + 120);
            return;
        }

        Scheduler.later(delay, () -> {
            if (s.over) return;
            say(s, "A)  " + node.a(), ChatFormatting.AQUA);
            say(s, "B)  " + node.b(), ChatFormatting.AQUA);
            say(s, "C)  " + node.c(), ChatFormatting.AQUA);
            say(s, "Type A, B or C  (" + VOTE_SECONDS.asInt() + " seconds)", ChatFormatting.GRAY);
            s.open = true;
            s.voteTimer = Scheduler.later(VOTE_SECONDS.asInt() * 20, () -> resolve(s));
        });
    }

    private void resolve(Session s) {
        if (s.over || !s.open) return;
        s.open = false;
        if (s.voteTimer != null) s.voteTimer.cancel();

        Map<String, Integer> counts = new HashMap<>();
        for (String v : s.votes.values()) counts.merge(v, 1, Integer::sum);
        String choice;
        if (counts.isEmpty()) {
            choice = new String[]{"a", "b", "c"}[R.nextInt(3)];
            say(s, "Silence... the fire chooses for you.", ChatFormatting.GRAY);
        } else {
            int top = 0;
            for (int n : counts.values()) top = Math.max(top, n);
            List<String> tied = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() == top) tied.add(e.getKey());
            }
            String starterVote = s.votes.get(s.starter);
            if (tied.size() > 1 && starterVote != null && tied.contains(starterVote)) choice = starterVote;
            else choice = tied.get(R.nextInt(tied.size()));
            say(s, "The group chose " + choice.toUpperCase() + " (A:" + counts.getOrDefault("a", 0) + "  B:"
                    + counts.getOrDefault("b", 0) + "  C:" + counts.getOrDefault("c", 0) + ")", ChatFormatting.GRAY);
        }
        String next = s.node + choice;
        Scheduler.later(30, () -> {
            if (!s.over) play(s, next);
        });
    }

    // ------------------------------------------------------------------ the end

    private void recordEnding(Session s, String endingId) {
        for (UUID id : s.players) {
            ServerPlayer p = s.server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            PlayerRecord record = PlayerDataStore.get(p);
            Set<String> found = record.endings.computeIfAbsent(s.story.id(), k -> new HashSet<>());
            boolean isNew = found.add(endingId);
            PlayerDataStore.markDirty();
            p.sendSystemMessage(Component.literal((isNew ? "New ending found! " : "You've seen this ending before. ")
                    + "(" + found.size() + "/" + ENDINGS_PER_STORY + " in this story)").withStyle(ChatFormatting.GREEN));
        }
    }

    private void finishLater(Session s, int ticks) {
        Scheduler.later(ticks, () -> finish(s, 0));
    }

    private void finish(Session s, int delay) {
        if (s.over) return;
        s.over = true;
        if (s.voteTimer != null) s.voteTimer.cancel();
        Runnable cleanup = () -> {
            s.scene.tearDown();
            for (UUID id : s.players) BY_PLAYER.remove(id);
        };
        if (delay > 0) Scheduler.later(delay, cleanup);
        else cleanup.run();
    }

    private static void say(Session s, String text, ChatFormatting color) {
        for (UUID id : s.players) {
            ServerPlayer p = s.server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal("  " + text).withStyle(color));
        }
    }
}
