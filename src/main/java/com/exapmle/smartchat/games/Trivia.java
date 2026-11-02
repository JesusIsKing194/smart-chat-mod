package com.example.smartchat.games;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft trivia: multiple choice, answer with A, B, C or D (or the answer itself). Streaks add bonus points
 * and the last question of a round is worth double.
 */
public final class Trivia implements GameManager.Game {

    public static final Setting ROUNDS = Settings.integer("games", "trivia_rounds", 8, 3, 30,
            "How many questions are in one trivia game (the last one is a double-points bonus).");

    public static final Setting POINTS = Settings.integer("games", "trivia_points", 10, 1, 100,
            "Points for a correct trivia answer (streaks add a bonus).");

    private static final Random R = new Random();
    private static final Map<UUID, Set<Integer>> SEEN = new HashMap<>();

    /** category, question, correct answer, then three wrong answers */
    private static final String[][] BANK = {
            // ---- mobs ----
            {"mobs", "How many hearts does a player start with?", "10", "5", "15", "20"},
            {"mobs", "Which mob explodes when it gets close to you?", "Creeper", "Zombie", "Skeleton", "Spider"},
            {"mobs", "What do you need to tame a wolf?", "Bones", "Raw fish", "Carrots", "Seeds"},
            {"mobs", "What do you feed pigs to breed them?", "Carrots", "Wheat", "Bones", "Sugar"},
            {"mobs", "Which mob can you cure with a golden apple and a Weakness potion?", "Zombie villager", "Husk", "Drowned", "Zombified piglin"},
            {"mobs", "Which mob can drop a trident?", "Drowned", "Guardian", "Pillager", "Husk"},
            {"mobs", "Which mob are creepers afraid of?", "Cats", "Wolves", "Villagers", "Iron golems"},
            {"mobs", "What makes an Enderman angry?", "Looking at its eyes", "Hitting a pig", "Placing a torch", "Standing still"},
            {"mobs", "Which mob drops Wither skeleton skulls?", "Wither skeleton", "Skeleton", "Stray", "Blaze"},
            {"mobs", "How many Wither skeleton skulls are needed to summon the Wither?", "3", "1", "2", "4"},
            {"mobs", "What do you need to steer a horse?", "Saddle", "Carrot on a stick", "Lead", "Bridle"},
            {"mobs", "Which villager profession uses a lectern?", "Librarian", "Farmer", "Cleric", "Mason"},
            {"mobs", "Which block is the farmer villager's workstation?", "Composter", "Barrel", "Furnace", "Loom"},
            {"mobs", "What do you use to collect honey from a bee nest?", "Glass bottle", "Bucket", "Bowl", "Sword"},
            {"mobs", "How much health does the Ender Dragon have?", "200", "100", "300", "500"},
            {"mobs", "Which Nether mob shoots big explosive fireballs and cries?", "Ghast", "Blaze", "Strider", "Hoglin"},
            {"mobs", "What happens to a villager hit by lightning?", "It becomes a witch", "It becomes a zombie", "It explodes", "Nothing"},
            {"mobs", "Which flying mob attacks players who haven't slept?", "Phantom", "Vex", "Allay", "Bat"},
            // ---- items ----
            {"items", "How many planks do you need for a crafting table?", "4", "2", "6", "8"},
            {"items", "How many obsidian blocks make the smallest Nether portal frame?", "10", "4", "12", "14"},
            {"items", "Which is the first pickaxe that can mine obsidian?", "Diamond", "Iron", "Stone", "Golden"},
            {"items", "What do you smelt ancient debris into?", "Netherite scrap", "Gold ingot", "Iron ingot", "Diamond"},
            {"items", "What is the center item of a beacon recipe?", "Nether star", "Diamond", "Emerald", "Eye of Ender"},
            {"items", "Which item is NOT needed to craft an enchanting table?", "Gold ingot", "Diamond", "Obsidian", "Book"},
            {"items", "What is the highest level an enchanting table can offer?", "30", "20", "50", "100"},
            {"items", "Which armor material is the strongest?", "Netherite", "Diamond", "Iron", "Gold"},
            {"items", "What is the maximum stack size of ender pearls?", "16", "64", "8", "32"},
            {"items", "How many iron ingots make a bucket?", "3", "2", "5", "9"},
            {"items", "Which block can't be broken in Survival mode?", "Bedrock", "Obsidian", "Ancient debris", "Deepslate"},
            {"items", "Which helmet gives you extra underwater breathing time?", "Turtle shell", "Iron helmet", "Leather cap", "Chainmail helmet"},
            {"items", "What do you use to find a stronghold?", "Eye of Ender", "Compass", "Map", "Spyglass"},
            {"items", "What does a compass point to by default?", "World spawn", "Nearest village", "Your bed", "The Nether"},
            {"items", "What can light TNT?", "Flint and steel", "Water bucket", "Shears", "Bone meal"},
            {"items", "Which ingredient makes a Night Vision potion?", "Golden carrot", "Apple", "Sugar", "Gunpowder"},
            // ---- world ----
            {"world", "Which dimension do Nether fortresses generate in?", "The Nether", "The Overworld", "The End", "All of them"},
            {"world", "How do you reach The End?", "Stronghold portal", "Nether portal", "A bed", "An elytra"},
            {"world", "Which biome has mooshrooms?", "Mushroom fields", "Plains", "Taiga", "Swamp"},
            {"world", "Where are ancient cities found?", "Deep dark", "Jungle", "Desert", "Ocean"},
            {"world", "What holds the End crystals?", "Obsidian pillars", "Bedrock towers", "End cities", "Strongholds"},
            {"world", "In which kind of biome do you find igloos?", "Snowy", "Desert", "Jungle", "Swamp"},
            {"world", "What is the lowest height in the Overworld?", "-64", "0", "-32", "-128"},
            {"world", "How tall is the Overworld build range?", "384 blocks", "256 blocks", "320 blocks", "512 blocks"},
            {"world", "How long is a full Minecraft day?", "20 minutes", "10 minutes", "30 minutes", "60 minutes"},
            {"world", "Where do you find the Heart of the Sea?", "Buried treasure", "Shipwreck", "Ocean monument", "Village"},
            {"world", "What is hidden at the center of an ocean monument?", "Gold blocks", "Diamonds", "Emeralds", "Tridents"},
            // ---- redstone and misc ----
            {"misc", "Which block detects a player stepping on it?", "Pressure plate", "Lever", "Button", "Daylight sensor"},
            {"misc", "Which block moves other blocks with redstone?", "Piston", "Dispenser", "Hopper", "Observer"},
            {"misc", "What do you use to tell the time?", "Clock", "Compass", "Map", "Bell"},
            {"misc", "Who created Minecraft?", "Notch", "Jeb", "Dinnerbone", "Herobrine"},
            {"misc", "Which company bought Mojang in 2014?", "Microsoft", "Google", "Sony", "Nintendo"},
            {"misc", "In what year was Minecraft's full 1.0 release?", "2011", "2009", "2013", "2015"},
            {"misc", "What do villagers trade with?", "Emeralds", "Gold", "Diamonds", "Iron"},
            {"misc", "What does the Hero of the Village effect give you?", "Trade discounts", "Flight", "Healing", "Speed"}
    };

    private static final class State {
        String category;
        int asked;
        int correct;
        int streak;
        int points;
        List<String> options = new ArrayList<>();
        String answer = "";
    }

    public static void register() {
        GameManager.register(new Trivia());
    }

    @Override
    public String id() {
        return "trivia";
    }

    @Override
    public String name() {
        return "Trivia";
    }

    @Override
    public List<String> triggers() {
        return Arrays.asList("trivia", "minecraft trivia", "quiz me", "play trivia", "quiz");
    }

    // ------------------------------------------------------------------ playing

    @Override
    public boolean start(ChatContext ctx, GameManager.Session session) {
        State state = new State();
        session.state = state;
        if (ctx.hasAny("mobs", "mob")) state.category = "mobs";
        else if (ctx.hasAny("items", "item", "crafting")) state.category = "items";
        else if (ctx.has("world")) state.category = "world";
        else if (ctx.hasAny("redstone", "misc")) state.category = "misc";
        GameManager.say(ctx, "Minecraft Trivia! " + ROUNDS.asInt() + " questions"
                + (state.category == null ? "" : " about " + state.category)
                + ". Answer with A, B, C or D. (Say 'skip' to pass, 'quit' to stop.)");
        ask(ctx, state);
        return true;
    }

    @Override
    public boolean onMessage(ChatContext ctx, GameManager.Session session) {
        State state = (State) session.state;

        if (ctx.hasAny("skip", "pass", "idk", "dont know")) {
            GameManager.say(ctx, "The answer was: " + state.answer + ".");
            state.streak = 0;
            next(ctx, state);
            return true;
        }

        int choice = parseChoice(ctx, state.options);
        if (choice < 0) {
            if (ctx.tokens.size() <= 4) {
                GameManager.say(ctx, "Answer with A, B, C or D.");
                return true;
            }
            return false; // ordinary chat, not an answer
        }

        boolean bonus = state.asked == ROUNDS.asInt();
        if (state.options.get(choice).equals(state.answer)) {
            state.streak++;
            state.correct++;
            int earned = POINTS.asInt() + Math.min(10, 2 * (state.streak - 1));
            if (bonus) earned *= 2;
            state.points += earned;
            GameManager.say(ctx, "Correct! +" + earned + " points" + (state.streak > 1 ? "  (streak: " + state.streak + ")" : "") + ".");
        } else {
            state.streak = 0;
            GameManager.say(ctx, "Not quite. The answer was: " + state.answer + ".");
        }
        next(ctx, state);
        return true;
    }

    private static int parseChoice(ChatContext ctx, List<String> options) {
        List<String> t = ctx.tokens;
        if (t.size() == 1 || (t.size() == 2 && (t.get(0).equals("option") || t.get(0).equals("answer") || t.get(0).equals("letter")))) {
            String letter = t.get(t.size() - 1);
            if (letter.length() == 1 && letter.charAt(0) >= 'a' && letter.charAt(0) <= 'd') return letter.charAt(0) - 'a';
        }
        int best = -1;
        int bestLength = 0;
        for (int i = 0; i < options.size(); i++) {
            String option = TextUtil.normalize(options.get(i));
            if (!option.isEmpty() && ctx.has(option) && option.length() > bestLength) {
                best = i;
                bestLength = option.length();
            }
        }
        return best;
    }

    private void next(ChatContext ctx, State state) {
        if (state.asked >= ROUNDS.asInt()) {
            finish(ctx, state);
        } else {
            ask(ctx, state);
        }
    }

    private void ask(ChatContext ctx, State state) {
        Set<Integer> seen = SEEN.computeIfAbsent(ctx.player.getUUID(), k -> new HashSet<>());
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < BANK.length; i++) {
            if (state.category != null && !BANK[i][0].equals(state.category)) continue;
            if (!seen.contains(i)) pool.add(i);
        }
        if (pool.isEmpty()) { // everything was seen once: start over
            for (int i = 0; i < BANK.length; i++) {
                if (state.category == null || BANK[i][0].equals(state.category)) pool.add(i);
            }
            seen.clear();
        }
        if (pool.isEmpty()) {
            finish(ctx, state);
            return;
        }
        int index = pool.get(R.nextInt(pool.size()));
        seen.add(index);
        String[] q = BANK[index];

        state.asked++;
        state.answer = q[2];
        state.options = new ArrayList<>(Arrays.asList(q[2], q[3], q[4], q[5]));
        Collections.shuffle(state.options, R);

        boolean bonus = state.asked == ROUNDS.asInt();
        GameManager.say(ctx, (bonus ? "BONUS question (double points)! " : "Question " + state.asked + "/" + ROUNDS.asInt() + ": ") + q[1]);
        GameManager.say(ctx, "A) " + state.options.get(0) + "    B) " + state.options.get(1)
                + "    C) " + state.options.get(2) + "    D) " + state.options.get(3));
    }

    // ------------------------------------------------------------------ the end

    private void finish(ChatContext ctx, State state) {
        ServerPlayer player = ctx.player;
        GameManager.addPoints(player, id(), state.points);
        GameManager.say(ctx, "Trivia finished! You got " + state.correct + " of " + state.asked + " right and earned "
                + state.points + " points.");
        if (state.asked > 0 && state.correct * 100 >= state.asked * 60) {
            GameManager.addWin(player, id());
            GameManager.say(ctx, "Great knowledge! You get a prize.");
            GameManager.prize(ctx);
        }
        GameManager.say(ctx, "Say 'trivia' to play again.");
        GameManager.end(player, "finished");
    }
}
