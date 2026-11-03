package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 20 Questions: think of a Minecraft mob, item or block (from the ones the assistant knows) and it asks yes/no
 * questions, always choosing the question that splits the remaining possibilities best.
 */
public final class TwentyQuestions implements GameManager.Game {

    private static final Random R = new Random();
    private static final int MAX_QUESTIONS = 20;

    private record Thing(String name, Set<String> attrs) {}

    private static final Map<String, String> QUESTIONS = new LinkedHashMap<>();
    private static final List<Thing> THINGS = new ArrayList<>();

    static {
        q("mob", "Is it a mob (a living creature)?");
        q("hostile", "Is it hostile?");
        q("passive", "Is it a passive or tameable animal?");
        q("tool", "Is it a tool or a weapon?");
        q("armor", "Is it armor or protective gear?");
        q("food", "Can you eat it?");
        q("block", "Is it a block you can place?");
        q("ore", "Do you mine it or smelt it?");
        q("nether", "Is it mostly found in the Nether?");
        q("end", "Is it mostly found in The End?");
        q("ocean", "Is it related to water or the ocean?");
        q("flying", "Can it fly?");
        q("explosive", "Can it explode?");
        q("redstone", "Is it related to redstone?");
        q("rare", "Is it rare or hard to get?");
        q("craftable", "Can you craft it?");
        q("metal", "Is it made of metal or a gem?");
        q("wood", "Is it made from wood?");
        q("farm", "Is it related to farming or plants?");
        q("undead", "Is it undead?");
        q("boss", "Is it a boss?");
        q("villager", "Is it related to villagers?");
        q("light", "Does it give off light?");

        // mobs
        t("Creeper", "mob,hostile,explosive");
        t("Zombie", "mob,hostile,undead");
        t("Skeleton", "mob,hostile,undead");
        t("Spider", "mob,hostile");
        t("Enderman", "mob,hostile,end");
        t("Blaze", "mob,hostile,nether,flying");
        t("Ghast", "mob,hostile,nether,flying,explosive");
        t("Wither", "mob,hostile,boss,nether,flying,undead,explosive,rare");
        t("Ender Dragon", "mob,hostile,boss,end,flying,rare");
        t("Phantom", "mob,hostile,flying,undead");
        t("Witch", "mob,hostile");
        t("Drowned", "mob,hostile,undead,ocean");
        t("Guardian", "mob,hostile,ocean");
        t("Slime", "mob,hostile");
        t("Piglin", "mob,hostile,nether");
        t("Warden", "mob,hostile,rare");
        t("Pig", "mob,passive");
        t("Cow", "mob,passive");
        t("Sheep", "mob,passive");
        t("Chicken", "mob,passive");
        t("Wolf", "mob,passive");
        t("Cat", "mob,passive");
        t("Horse", "mob,passive");
        t("Fox", "mob,passive");
        t("Villager", "mob,passive,villager");
        t("Iron Golem", "mob,villager");
        t("Bee", "mob,passive,flying,farm");
        t("Squid", "mob,passive,ocean");
        t("Dolphin", "mob,passive,ocean");
        t("Turtle", "mob,passive,ocean");
        t("Axolotl", "mob,passive,ocean,rare");
        t("Parrot", "mob,passive,flying");
        t("Bat", "mob,passive,flying");
        t("Strider", "mob,passive,nether");
        t("Allay", "mob,passive,flying,rare");
        // tools, weapons and armor
        t("Diamond Sword", "tool,craftable,metal");
        t("Wooden Pickaxe", "tool,craftable,wood");
        t("Iron Pickaxe", "tool,craftable,metal");
        t("Bow", "tool,craftable");
        t("Trident", "tool,ocean,rare");
        t("Bucket", "tool,craftable,metal");
        t("Fishing Rod", "tool,craftable,ocean,wood");
        t("Compass", "tool,craftable,redstone,metal");
        t("Clock", "tool,craftable,metal");
        t("Map", "tool,craftable");
        t("Shield", "armor,craftable,wood");
        t("Netherite Chestplate", "armor,craftable,metal,rare,nether");
        t("Diamond Helmet", "armor,craftable,metal");
        t("Elytra", "armor,end,flying,rare");
        // food
        t("Golden Apple", "food,craftable,metal");
        t("Enchanted Golden Apple", "food,rare,metal");
        t("Bread", "food,craftable,farm");
        t("Cake", "food,craftable,farm");
        t("Apple", "food,farm");
        t("Carrot", "food,farm");
        t("Steak", "food");
        // materials and blocks
        t("Diamond", "ore,metal,rare");
        t("Iron Ingot", "ore,metal");
        t("Emerald", "ore,metal,villager,rare");
        t("Coal", "ore");
        t("Redstone Dust", "redstone,ore");
        t("Obsidian", "block,rare");
        t("Ancient Debris", "block,ore,nether,rare");
        t("Netherite Ingot", "metal,nether,rare,craftable");
        t("TNT", "block,explosive,craftable");
        t("Crafting Table", "block,craftable,wood");
        t("Furnace", "block,craftable");
        t("Chest", "block,craftable,wood");
        t("Torch", "block,craftable,light");
        t("Glowstone", "block,nether,light");
        t("Beacon", "block,craftable,light,rare");
        t("Nether Star", "nether,rare");
        t("Ender Pearl", "end");
        t("Eye of Ender", "end,craftable,rare");
        t("Piston", "block,redstone,craftable");
        t("Lever", "block,redstone,craftable");
        t("Hopper", "block,redstone,craftable,metal");
        t("Redstone Lamp", "block,redstone,light,craftable");
        t("Bed", "block,craftable");
        t("Enchanting Table", "block,craftable,rare");
        t("Anvil", "block,craftable,metal");
        t("Ender Chest", "block,craftable,end");
        t("Shulker Box", "block,craftable,end,rare");
        t("Conduit", "block,ocean,rare,light");
        t("Sponge", "block,ocean");
        t("Prismarine", "block,ocean");
        t("Oak Log", "block,wood");
        t("Cobblestone", "block");
        t("Sand", "block");
        t("Pumpkin", "block,farm");
        t("Sugar Cane", "farm");
        t("Saddle", "rare");
        t("Name Tag", "rare");
        t("Totem of Undying", "rare,villager");
    }

    private static void q(String attr, String question) {
        QUESTIONS.put(attr, question);
    }

    private static void t(String name, String attrs) {
        THINGS.add(new Thing(name, new HashSet<>(Arrays.asList(attrs.split(",")))));
    }

    private static final class State {
        boolean ready;
        List<Thing> candidates = new ArrayList<>(THINGS);
        Set<String> asked = new HashSet<>();
        String currentAttr;
        Thing guess;
        int count;
    }

    public static void register() {
        GameManager.register(new TwentyQuestions());
    }

    @Override
    public String id() {
        return "twenty";
    }

    @Override
    public String name() {
        return "20 Questions";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("20 questions", "twenty questions", "guess my item", "guess what im thinking");
    }

    // ------------------------------------------------------------------ playing

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        session.state = new State();
        GameManager.say(ctx, "20 Questions! Think of a Minecraft mob, item or block (I know about " + THINGS.size()
                + " of them). I'll ask yes/no questions and try to guess it. Say 'ready' when you have one!");
        return true;
    }

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;

        if (!state.ready) {
            if (answer(ctx) != 0 || ctx.hasAny("ready", "ok", "okay", "go", "start", "got one", "i have one")) {
                state.ready = true;
                askNext(ctx, state);
                return true;
            }
            if (ctx.tokens.size() <= 3) {
                GameManager.say(ctx, "Say 'ready' when you've thought of something!");
                return true;
            }
            return false;
        }

        int a = answer(ctx); // 1 = yes, -1 = no, 2 = not sure, 0 = not an answer
        if (a == 0) {
            if (ctx.tokens.size() <= 3) {
                GameManager.say(ctx, "Please answer yes, no, or 'not sure'.");
                return true;
            }
            return false;
        }

        if (state.guess != null) { // answering "Is it a ...?"
            if (a == 1) {
                assistantWins(ctx, state);
            } else {
                state.candidates.remove(state.guess);
                state.guess = null;
                if (state.candidates.isEmpty() || state.count >= MAX_QUESTIONS) playerWins(ctx, state);
                else askNext(ctx, state);
            }
            return true;
        }

        // answering a question
        if (a == 1 || a == -1) {
            boolean yes = a == 1;
            String attr = state.currentAttr;
            state.candidates.removeIf(thing -> thing.attrs().contains(attr) != yes);
        }
        if (state.candidates.isEmpty()) {
            playerWins(ctx, state);
            return true;
        }
        askNext(ctx, state);
        return true;
    }

    /** 1 = yes, -1 = no, 2 = not sure, 0 = something else. */
    private static int answer(ChatContext ctx) {
        if (ctx.tokens.size() > 4) return 0;
        if (ctx.hasAny("yes", "yeah", "yep", "yup", "y", "correct", "sure", "right", "true", "ye")) return 1;
        if (ctx.hasAny("no", "nope", "nah", "n", "wrong", "false")) return -1;
        if (ctx.hasAny("maybe", "sometimes", "not sure", "idk", "dont know", "unsure", "kind of", "sort of")) return 2;
        return 0;
    }

    // ------------------------------------------------------------------ asking and guessing

    private void askNext(ChatContext ctx, State state) {
        if (state.count >= MAX_QUESTIONS || state.candidates.size() == 1) {
            makeGuess(ctx, state);
            return;
        }
        // pick the question that splits the remaining candidates closest to half and half
        String best = null;
        double bestScore = Double.MAX_VALUE;
        int total = state.candidates.size();
        for (String attr : QUESTIONS.keySet()) {
            if (state.asked.contains(attr)) continue;
            int yes = 0;
            for (Thing thing : state.candidates) {
                if (thing.attrs().contains(attr)) yes++;
            }
            if (yes == 0 || yes == total) continue; // tells us nothing
            double score = Math.abs(yes - total / 2.0);
            if (score < bestScore) {
                bestScore = score;
                best = attr;
            }
        }
        if (best == null) {
            makeGuess(ctx, state);
            return;
        }
        state.asked.add(best);
        state.currentAttr = best;
        state.count++;
        GameManager.say(ctx, "Question " + state.count + ": " + QUESTIONS.get(best));
    }

    private void makeGuess(ChatContext ctx, State state) {
        if (state.candidates.isEmpty()) {
            playerWins(ctx, state);
            return;
        }
        state.guess = state.candidates.get(R.nextInt(state.candidates.size()));
        state.count++;
        GameManager.say(ctx, "I think I know... is it the " + state.guess.name() + "?");
    }

    // ------------------------------------------------------------------ the end

    private void assistantWins(ChatContext ctx, State state) {
        ServerPlayer player = ctx.player;
        GameManager.addPoints(player, id(), 5);
        GameManager.say(ctx, "I got it in " + state.count + " questions! +5 points for playing. Say '20 questions' for another round.");
        GameManager.end(player, "finished");
    }

    private void playerWins(ChatContext ctx, State state) {
        ServerPlayer player = ctx.player;
        GameManager.addPoints(player, id(), 25);
        GameManager.addWin(player, id());
        GameManager.say(ctx, "You stumped me! I couldn't work it out. +25 points.");
        GameManager.prize(ctx);
        GameManager.say(ctx, "Say '20 questions' to play again.");
        GameManager.end(player, "finished");
    }
}
