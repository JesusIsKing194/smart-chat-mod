package com.example.smartchat.ai;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.teleport.Finder;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.Scheduler;
import com.example.smartchat.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The chat assistant's personality.
 * Register an instance (priority about 90) for jokes, stories, the magic 8-ball, fortunes and small talk,
 * and a AiHandler.Fallback (priority about 900, after every other handler) for requests nobody understood.
 * Small talk like "hello" or "thanks" only gets an answer when the message is aimed at the assistant:
 * its name is used, or it spoke to the player a moment ago.
 */
public final class AiHandler implements ChatRouter.Handler {

    public static final Setting NAMES = Settings.list("ai", "names",
            "smart chat,smartchat,smarty,assistant,chatbot,chat bot,hey bot,ok bot,yo bot",
            "Names that make the assistant answer small talk (\"hey smarty, how are you\").");

    public static final Setting CREATOR = Settings.text("ai", "creator_name", "Isaiah",
            "The name the assistant gives when someone asks who made it.");

    public static final Setting CONVERSATION_SECONDS = Settings.integer("ai", "conversation_seconds", 45, 5, 600,
            "After the assistant talks to a player, short follow-ups (\"thanks\", \"again\", \"how are you\") count as "
                    + "being aimed at it for this many seconds.");

    public static final Setting REPLY_COOLDOWN = Settings.integer("ai", "reply_cooldown_seconds", 2, 0, 60,
            "Minimum seconds between the assistant's replies to the same player.");

    public static final Setting REPLY_TO_UNKNOWN = Settings.bool("ai", "reply_to_unknown", true,
            "When someone clearly asks the assistant for something it doesn't understand, it says so instead of staying silent.");

    /** What the assistant remembers about one player's recent chat. */
    private static final class Memory {
        long lastTime = 0;
        String lastTopic = "";
        String lastQuestion = "";
        int streak = 0;
    }

    private static final Map<UUID, Memory> MEMORY = new HashMap<>();
    private static final Map<UUID, Long> LAST_REPLY = new HashMap<>();

    /** Touching this method makes sure the settings above are registered. */
    public static void init() {}

    // ------------------------------------------------------------------ helpers

    private static Memory memory(ServerPlayer player) {
        return MEMORY.computeIfAbsent(player.getUUID(), k -> new Memory());
    }

    private static boolean mentionsName(ChatContext ctx) {
        for (String name : NAMES.asList()) {
            if (ctx.has(name)) return true;
        }
        return false;
    }

    private static boolean quiet(ServerPlayer player) {
        return PlayerDataStore.get(player).quietAi;
    }

    private static boolean onCooldown(ServerPlayer player) {
        int cooldown = REPLY_COOLDOWN.asInt();
        Long last = LAST_REPLY.get(player.getUUID());
        return cooldown > 0 && last != null && System.currentTimeMillis() - last < cooldown * 1000L;
    }

    private static String fill(ChatContext ctx, String line) {
        return Pools.fill(line, ctx.player.getName().getString(), CREATOR.asText());
    }

    /** Sends one line, remembers the topic and counts how many times in a row the player asked for it. */
    private static boolean say(ChatContext ctx, Memory mem, String topic, String line) {
        Messages.say(ctx.player, fill(ctx, line));
        touch(ctx, mem, topic);
        return true;
    }

    private static void touch(ChatContext ctx, Memory mem, String topic) {
        mem.streak = topic.equals(mem.lastTopic) ? mem.streak + 1 : 1;
        mem.lastTopic = topic;
        mem.lastTime = System.currentTimeMillis();
        LAST_REPLY.put(ctx.player.getUUID(), mem.lastTime);
    }

    /** Sends several lines one after another with a short pause between them. */
    private static void sendSpaced(ServerPlayer player, String[] lines, int gapTicks) {
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.isBlank()) continue;
            if (i == 0) Messages.say(player, line);
            else Scheduler.later(i * gapTicks, () -> Messages.say(player, line));
        }
    }

    private static String currentBiome(ChatContext ctx) {
        try {
            Holder<Biome> holder = ctx.level.getBiome(ctx.player.blockPosition());
            String registered = holder.getRegisteredName(); // for example "minecraft:dark_forest"
            int colon = registered.indexOf(':');
            String path = colon >= 0 ? registered.substring(colon + 1) : registered;
            return Finder.prettyBiome(path).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "wilderness";
        }
    }

    // ------------------------------------------------------------------ main handler

    @Override
    public boolean handle(ChatContext ctx) {
        if (!Settings.AI_REPLIES.asBoolean()) return false;
        ServerPlayer player = ctx.player;
        if (quiet(player)) return false;
        if (onCooldown(player)) return false;

        Memory mem = memory(player);
        long now = System.currentTimeMillis();
        boolean named = mentionsName(ctx);
        boolean inChat = mem.lastTime > 0 && now - mem.lastTime < CONVERSATION_SECONDS.asInt() * 1000L;
        boolean addressed = named || inChat;
        boolean request = ctx.intents().requestCue || ctx.polite || ctx.tokens.size() <= 3;

        // 1. things people ask for outright
        if (ctx.hasAny("magic 8 ball", "8 ball", "eight ball", "magic eight ball", "magic ball")) {
            return eightBall(ctx, mem);
        }
        if (ctx.hasAny("my fortune", "fortune cookie", "tell fortune", "tell me a fortune", "read my fortune",
                "fortune teller", "whats my fortune", "what is my fortune")
                || (ctx.tokens.size() == 1 && ctx.has("fortune"))) {
            return say(ctx, mem, "fortune", "Your fortune: " + Pools.fortune());
        }
        if (request && (ctx.hasAny("joke", "jokes", "make me laugh", "something funny", "cheer me up"))) {
            return joke(ctx, mem);
        }
        if (request && ctx.hasAny("story", "tale")
                && !ctx.hasAny("campfire", "scary", "horror", "creepy", "spooky", "ghost story", "stories")) {
            return story(ctx, mem);
        }
        if (ctx.hasAny("entertain me", "give me something to do")
                || (addressed && ctx.hasAny("im bored", "i am bored", "so bored", "nothing to do"))) {
            return say(ctx, mem, "bored", Pools.bored());
        }

        // 2. "again", "another one"
        if (inChat && ctx.tokens.size() <= 4 && ctx.hasAny("again", "another one", "another", "one more", "more")) {
            return again(ctx, mem);
        }

        // 3. bare politeness ("thanks", "ty", "plz") is never an item request
        if (ctx.polite && ctx.tokens.size() <= 4 && ctx.itemRequest() == null) {
            if (addressed && thanksWords(ctx)) return say(ctx, mem, "thanks", Pools.thanks());
            return true; // swallowed quietly: someone thanking a friend is not for the item handler
        }

        // 4. small talk, only when it is aimed at the assistant
        if (!addressed) return false;
        return smallTalk(ctx, mem);
    }

    private static boolean thanksWords(ChatContext ctx) {
        return ctx.hasAny("thanks", "thank you", "thx", "ty", "tysm", "thank");
    }

    private boolean smallTalk(ChatContext ctx, Memory mem) {
        if (ctx.hasAny("i love you", "love you", "ily")) return say(ctx, mem, "love", Pools.love());
        if (ctx.hasAny("good bot", "good job", "nice job", "well done", "you are smart", "youre smart",
                "you are awesome", "youre awesome", "you are the best", "youre the best", "you are amazing", "youre amazing")) {
            return say(ctx, mem, "compliment", Pools.compliment());
        }
        if (ctx.hasAny("bad bot", "you suck", "youre dumb", "you are dumb", "youre stupid", "you are stupid",
                "stupid bot", "youre useless", "you are useless", "shut up")) {
            return say(ctx, mem, "insult", Pools.insult());
        }
        if (ctx.hasAny("who made you", "who created you", "who built you", "who programmed you", "your creator",
                "who made this mod", "who is your creator")) {
            return say(ctx, mem, "creator", Pools.creator());
        }
        if (ctx.hasAny("are you alive", "are you real", "are you human", "are you a robot", "are you sentient",
                "do you have feelings", "are you conscious")) {
            return say(ctx, mem, "alive", Pools.alive());
        }
        if (ctx.hasAny("how are you", "how r u", "how are u", "hows it going", "how is it going", "whats up")) {
            return say(ctx, mem, "how", Pools.howAreYou());
        }
        if (ctx.hasAny("your name", "who are you", "what are you")) {
            return say(ctx, mem, "name", Pools.nameLine());
        }
        if (thanksWords(ctx) && ctx.tokens.size() <= 5) return say(ctx, mem, "thanks", Pools.thanks());
        if (ctx.tokens.size() <= 6 && ctx.hasAny("bye", "goodbye", "cya", "goodnight", "good night", "farewell",
                "see you", "see ya", "later")) {
            return say(ctx, mem, "bye", Pools.bye());
        }
        if (ctx.tokens.size() <= 6 && ctx.hasAny("hello", "hi", "hey", "heya", "yo", "sup", "hola", "greetings", "howdy")) {
            return say(ctx, mem, "hello", Pools.hello());
        }
        return false;
    }

    // ------------------------------------------------------------------ topics

    private boolean joke(ChatContext ctx, Memory mem) {
        String line = fill(ctx, Pools.joke());
        touch(ctx, mem, "joke");
        int q = line.indexOf("? ");
        if (q > 0 && q < line.length() - 3) {
            // question first, punchline after a short pause
            sendSpaced(ctx.player, new String[]{line.substring(0, q + 1), line.substring(q + 2).trim()}, 45);
        } else {
            Messages.say(ctx.player, line);
        }
        if (mem.streak == 3) {
            Scheduler.later(100, () -> Messages.say(ctx.player, "You really like jokes! Say 'another' for more."));
        }
        return true;
    }

    private boolean story(ChatContext ctx, Memory mem) {
        String text = Pools.story(currentBiome(ctx), ctx.player.getName().getString());
        touch(ctx, mem, "story");
        sendSpaced(ctx.player, text.split("(?<=[.!?])\\s+"), 40);
        return true;
    }

    private boolean eightBall(ChatContext ctx, Memory mem) {
        String question = ctx.text;
        for (String phrase : new String[]{"magic eight ball", "magic 8 ball", "magic ball", "eight ball", "8 ball"}) {
            question = TextUtil.removePhrase(question, phrase);
        }
        if (question.isBlank()) {
            return say(ctx, mem, "eightball", "Ask the magic 8-ball a question, like 'magic 8 ball will I find diamonds'.");
        }
        mem.lastQuestion = question;
        return say(ctx, mem, "eightball", "The magic 8-ball says: " + Pools.eightBall(question));
    }

    private boolean again(ChatContext ctx, Memory mem) {
        switch (mem.lastTopic) {
            case "joke": return joke(ctx, mem);
            case "story": return story(ctx, mem);
            case "fortune": return say(ctx, mem, "fortune", "Your fortune: " + Pools.fortune());
            case "eightball":
                return say(ctx, mem, "eightball", "The 8-ball shakes again: " + Pools.eightBall(mem.lastQuestion));
            case "bored": return say(ctx, mem, "bored", Pools.bored());
            default: return false;
        }
    }

    // ------------------------------------------------------------------ fallback

    /**
     * Register this last. It answers when someone clearly spoke to the assistant (used its name, or politely asked it
     * for something) and no other handler understood.
     */
    public static final class Fallback implements ChatRouter.Handler {

        @Override
        public boolean handle(ChatContext ctx) {
            if (!Settings.AI_REPLIES.asBoolean() || !REPLY_TO_UNKNOWN.asBoolean() || !Settings.HINTS.asBoolean()) {
                return false;
            }
            ServerPlayer player = ctx.player;
            if (quiet(player) || onCooldown(player)) return false;

            boolean named = mentionsName(ctx);
            boolean politeAsk = ctx.polite && ctx.hasAny("can you", "could you", "would you", "will you", "can i",
                    "could i", "may i", "i want", "i need", "give me", "get me", "make me", "show me", "tell me");
            if (!named && !politeAsk) return false;

            Memory mem = memory(player);
            return say(ctx, mem, "unknown", Pools.unknown());
        }
    }
}
