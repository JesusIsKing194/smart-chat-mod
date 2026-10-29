package com.example.smartchat.ai;

import com.example.smartchat.util.TextUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 66 riddles in three difficulty tiers, each with three hints that get more helpful.
 * Answer checking forgives extra words and small typos.
 */
public final class Riddles {

    public enum Tier {
        EASY(10), MEDIUM(20), HARD(30);

        /** Points for a correct answer with no hints used. */
        public final int points;

        Tier(int points) {
            this.points = points;
        }
    }

    /** Points taken off for every hint that was shown. */
    public static final int HINT_COST = 5;

    public record Riddle(String id, Tier tier, String question, List<String> answers, List<String> hints) {}

    private static final List<Riddle> ALL = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int counter = 0;

    private Riddles() {}

    private static void add(Tier tier, String question, String answers, String h1, String h2, String h3) {
        counter++;
        String id = String.format("r%02d", counter);
        List<String> answerList = new ArrayList<>();
        for (String a : answers.split("\\|")) answerList.add(TextUtil.normalize(a));
        ALL.add(new Riddle(id, tier, question, answerList, Arrays.asList(h1, h2, h3)));
    }

    // ------------------------------------------------------------------ public API

    public static List<Riddle> all() {
        return ALL;
    }

    public static int count(Tier tier) {
        int n = 0;
        for (Riddle r : ALL) {
            if (r.tier() == tier) n++;
        }
        return n;
    }

    /** "easy", "medium" or "hard" (anything else gives null). */
    public static Tier tierByName(String name) {
        if (name == null) return null;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "easy": return Tier.EASY;
            case "medium": case "normal": return Tier.MEDIUM;
            case "hard": return Tier.HARD;
            default: return null;
        }
    }

    /** A random riddle of this tier that is not in the seen set (when everything was seen, any riddle). */
    public static Riddle random(Tier tier, Set<String> seen) {
        List<Riddle> fresh = new ArrayList<>();
        List<Riddle> any = new ArrayList<>();
        for (Riddle r : ALL) {
            if (r.tier() != tier) continue;
            any.add(r);
            if (seen == null || !seen.contains(r.id())) fresh.add(r);
        }
        List<Riddle> pool = fresh.isEmpty() ? any : fresh;
        return pool.isEmpty() ? null : pool.get(RANDOM.nextInt(pool.size()));
    }

    public static Riddle byId(String id) {
        for (Riddle r : ALL) {
            if (r.id().equals(id)) return r;
        }
        return null;
    }

    /** The hint to show after hintsUsed hints were already given (0 = the first hint). */
    public static String hint(Riddle riddle, int hintsUsed) {
        List<String> hints = riddle.hints();
        return hints.get(Math.max(0, Math.min(hintsUsed, hints.size() - 1)));
    }

    /** Points for a correct answer after the given number of hints (never less than 1). */
    public static int score(Riddle riddle, int hintsUsed) {
        return Math.max(1, riddle.tier().points - HINT_COST * hintsUsed);
    }

    /** True when the guess contains an accepted answer, or a word that is just a small typo of one. */
    public static boolean check(Riddle riddle, String guess) {
        String g = TextUtil.normalize(guess);
        if (g.isEmpty()) return false;
        List<String> tokens = TextUtil.tokens(g);
        for (String answer : riddle.answers()) {
            if (answer.isEmpty()) continue;
            if (TextUtil.containsPhrase(g, answer)) return true;
            if (!answer.contains(" ") && answer.length() >= 5) {
                for (String token : tokens) {
                    if (Math.abs(token.length() - answer.length()) <= 2 && TextUtil.similarity(token, answer) >= 0.8) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The first accepted answer, nicely written, for "the answer was ...". */
    public static String answerText(Riddle riddle) {
        return riddle.answers().get(0);
    }

    // ------------------------------------------------------------------ the riddles

    static {
        // ---------------- EASY ----------------
        add(Tier.EASY, "What has hands but can't clap?", "clock|watch",
                "You check me to see the time.", "I tick all day long.", "I hang on walls and sit on wrists, and I have a face.");
        add(Tier.EASY, "What gets wetter the more it dries?", "towel",
                "You use me after a shower.", "I'm soft and made of cloth.", "I dry you off.");
        add(Tier.EASY, "What has keys but can't open locks?", "piano|keyboard",
                "I make music.", "I have black and white keys.", "You press my keys to play songs.");
        add(Tier.EASY, "I'm green, I sneak up on you, and I go boom. What am I?", "creeper",
                "I'm a mob you should never ignore.", "I'm green and I hiss.", "I explode.");
        add(Tier.EASY, "What has a neck but no head?", "bottle",
                "You can pour from me.", "I'm made of glass or plastic.", "Water often lives inside me.");
        add(Tier.EASY, "What goes up when the rain comes down?", "umbrella",
                "I keep you dry.", "I open up when it's wet outside.", "You hold me over your head.");
        add(Tier.EASY, "What has one eye but cannot see?", "needle",
                "I'm used for sewing.", "Thread goes through my eye.", "I'm thin and sharp.");
        add(Tier.EASY, "What do you break just by saying its name?", "silence",
                "You break me by speaking.", "I'm the opposite of noise.", "Libraries love me.");
        add(Tier.EASY, "What is full of holes but still holds water?", "sponge",
                "You use me for cleaning.", "I'm soft and full of tiny holes.", "I soak up water.");
        add(Tier.EASY, "What can you catch but not throw?", "cold",
                "You might get me in winter.", "Tissues are my best friends.", "I make you sneeze.");
        add(Tier.EASY, "What has teeth but cannot bite?", "comb|saw",
                "You use me on hair.", "I have many teeth in a row.", "I tidy your hair.");
        add(Tier.EASY, "I'm tall when I'm young and short when I'm old. What am I?", "candle",
                "I give a little light.", "Fire melts me slowly.", "I'm made of wax.");
        add(Tier.EASY, "In Minecraft I'm crafted from logs, and you need me to make almost everything. What am I?",
                "planks|plank|wooden planks",
                "I'm made from logs.", "One log makes four of me.", "Crafting tables and sticks need me.");
        add(Tier.EASY, "I glow in the dark, you place me on walls, and I keep mobs away. What am I?", "torch",
                "You place me on walls.", "I'm made from coal and a stick.", "Mobs don't spawn near my light.");
        add(Tier.EASY, "What can travel around the world while staying in one corner?", "stamp",
                "I stick to an envelope.", "I travel through the mail.", "I'm small and rectangular.");
        add(Tier.EASY, "What begins with T, ends with T, and has T in it?", "teapot",
                "I hold a hot drink.", "I have a spout and a handle.", "I'm found at tea parties.");
        add(Tier.EASY, "What has a thumb and four fingers but is not alive?", "glove",
                "I fit your hand.", "I come in pairs.", "I keep your hands warm.");
        add(Tier.EASY, "What gets bigger the more you take away from it?", "hole",
                "Think about digging.", "The more you remove, the bigger I get.", "I'm an empty space in the ground.");
        add(Tier.EASY, "I fall from the clouds, I fill your cauldron, and plants love me. What am I?", "rain",
                "I fall from the sky.", "Plants drink me up.", "I put out fires in the Overworld.");
        add(Tier.EASY, "I'm a glowing block found hanging from the Nether's ceiling. What am I?", "glowstone",
                "I'm found in the Nether.", "I glow bright yellow.", "I hang from ceilings in clusters.");
        add(Tier.EASY, "What has legs but doesn't walk?", "table|chair",
                "I'm in every kitchen.", "I have four legs and a flat top.", "You eat dinner on me.");
        add(Tier.EASY, "What is always in front of you but can't be seen?", "future",
                "I'm always ahead.", "I haven't happened yet.", "Tomorrow is part of me.");

        // ---------------- MEDIUM ----------------
        add(Tier.MEDIUM, "The more you take, the more you leave behind. What am I?", "footsteps|footstep|steps",
                "You make me when you walk.", "Look behind you after a walk in the snow.", "I'm the marks you leave on the ground.");
        add(Tier.MEDIUM, "What can fill a room but takes up no space?", "light",
                "Switch me on.", "I travel faster than anything.", "I chase away darkness.");
        add(Tier.MEDIUM, "I speak without a mouth and hear without ears. I come alive with sound. What am I?", "echo",
                "Shout in a canyon.", "I repeat what you say.", "I come back to you.");
        add(Tier.MEDIUM, "What has cities but no houses, mountains but no trees, and water but no fish?", "map",
                "You use me to find your way.", "I show places on paper.", "I'm flat and you can fold me.");
        add(Tier.MEDIUM, "What word is spelled wrong in every dictionary?", "wrong",
                "Read the question again.", "It's the word itself.", "Spelling it as W-R-O-N-G is... right.");
        add(Tier.MEDIUM, "I'm dug from the ground, locked in a wooden case, and used by almost everyone to write. What am I?",
                "pencil lead|pencil|graphite|lead",
                "I'm used for writing.", "I'm dug out of the ground.", "I live in a wooden case.");
        add(Tier.MEDIUM, "What runs but never walks, has a mouth but never talks, and has a bed but never sleeps?", "river",
                "I flow to the sea.", "I have a bank, a bed and a mouth.", "Boats float on me.");
        add(Tier.MEDIUM, "You see me once in June, twice in November and never in May. What am I?", "letter e|e",
                "Think about the spelling.", "June has one, November has two.", "I'm the most common letter in English.");
        add(Tier.MEDIUM, "I'm tall and dark, I teleport, and you must never look me in the eyes. What am I?", "enderman",
                "I'm tall and dark.", "I teleport.", "I carry blocks.");
        add(Tier.MEDIUM, "I follow you all day but disappear in the dark. What am I?", "shadow",
                "I follow you in the sun.", "I vanish in the dark.", "I'm always beside you on the ground.");
        add(Tier.MEDIUM, "What has to be broken before you can use it?", "egg",
                "It's a breakfast item.", "I have a shell.", "A chicken made me.");
        add(Tier.MEDIUM, "I'm not alive, but I grow. I don't have lungs, but I need air. Water kills me. What am I?", "fire",
                "I'm hot.", "I need air to survive.", "Water ends me.");
        add(Tier.MEDIUM, "What goes through towns and fields but never moves?", "road|path",
                "Cars drive on me.", "I connect towns.", "I'm paved.");
        add(Tier.MEDIUM, "I float in the Nether, shoot fireballs and cry. What am I?", "ghast",
                "I'm found in the Nether.", "I float and shoot fireballs.", "I'm white and I cry.");
        add(Tier.MEDIUM, "What building has the most stories?", "library",
                "It's a quiet place.", "It's full of books.", "Lots of stories, but not a skyscraper.");
        add(Tier.MEDIUM, "What can you hold in your right hand but never in your left?", "left hand|your left hand",
                "Think about your own body.", "Try shaking it with the hand that's holding it.", "It's your opposite hand.");
        add(Tier.MEDIUM, "I'm light as a feather, yet the strongest player can't hold me for long. What am I?", "breath|your breath",
                "Everyone does it.", "You can hold me for a little while.", "I'm what you take before diving.");
        add(Tier.MEDIUM, "What comes once in a minute, twice in a moment, but never in a thousand years?", "letter m|m",
                "Think about the spelling of the words.", "Count the letters in 'minute' and 'moment'.", "It's a letter.");
        add(Tier.MEDIUM, "I'm a rare blue gem found deep underground, and the best tools are made from me. What am I?", "diamond|diamonds",
                "I'm blue and sparkly.", "I'm found deep underground.", "You craft strong tools from me.");
        add(Tier.MEDIUM, "What has four wheels and flies?", "garbage truck|trash truck|garbage can|dump truck|garbage",
                "Think about the word 'flies'.", "It's a vehicle that collects something smelly.", "Flies follow it.");
        add(Tier.MEDIUM, "What has a ring but no finger?", "telephone|phone",
                "It rings.", "You answer me.", "I fit in your pocket.");
        add(Tier.MEDIUM, "Forward I'm heavy, but backward I'm not. What am I?", "ton",
                "Say the word backward.", "It's a three-letter word.", "It's a unit of weight.");

        // ---------------- HARD ----------------
        add(Tier.HARD, "What can be cracked, made, told and played?", "joke",
                "You can crack me.", "People tell me.", "I make you laugh.");
        add(Tier.HARD, "I have branches but no fruit, trunk or leaves. What am I?", "bank",
                "I have branches in many cities.", "Money lives in me.", "A river also has one of my parts.");
        add(Tier.HARD, "What belongs to you but is used more by others?", "name|your name",
                "You've had me since birth.", "Others say me more than you.", "People call you by me.");
        add(Tier.HARD, "I swoop down at night on players who haven't slept. What am I?", "phantom|phantoms",
                "I swoop at night.", "You get me when you haven't slept.", "I'm a flying undead.");
        add(Tier.HARD, "A man pushes his car to a hotel and tells the owner he's bankrupt. Why?", "monopoly|board game",
                "It's not a real hotel emergency.", "He's playing a game.", "It's a board game about property.");
        add(Tier.HARD, "Which is heavier: a pound of feathers or a pound of bricks?", "same|neither|equal|the same",
                "Think about weight.", "A pound is a pound.", "They are equal.");
        add(Tier.HARD, "I have a spine but no bones, and many stories but no mouth. What am I?", "book",
                "I have pages.", "You read me.", "I sit on shelves.");
        add(Tier.HARD, "What invention lets you look right through a wall?", "window",
                "I let light in.", "I'm made of glass.", "I'm found in walls.");
        add(Tier.HARD, "I float above the End islands, I heal the dragon, and I explode. What am I?", "end crystal|crystal|ender crystal",
                "I'm in the End.", "I heal the dragon.", "I sit on obsidian pillars.");
        add(Tier.HARD, "I never ask questions, but I'm always answered. What am I?", "doorbell|bell",
                "I'm at your front door.", "Visitors press me.", "I ring.");
        add(Tier.HARD, "What five-letter word becomes shorter when you add two letters to it?", "short",
                "Think of a word that means 'not long'.", "It's five letters.", "Add 'er' and it gets shorter... literally.");
        add(Tier.HARD, "What starts with an E, ends with an E, but only contains one letter?", "envelope",
                "It holds a letter.", "It's a post office item.", "Only one letter is inside... a letter.");
        add(Tier.HARD, "I'm crafted from four scraps and four gold, and I make the strongest gear. What am I?",
                "netherite ingot|netherite",
                "Craft me with scraps.", "I use four gold too.", "I beat diamond.");
        add(Tier.HARD, "A rooster lays an egg on top of a pointed roof. Which way does it roll?",
                "roosters dont lay eggs|rooster doesnt lay eggs|they dont|it doesnt|roosters dont",
                "Think about roosters.", "It's a trick question.", "Roosters can't do that.");
        add(Tier.HARD, "I'm worn on the chest, I'm not a bird, but I fly through the End with a firework boost. What am I?", "elytra",
                "Wings!", "I'm found in End ships.", "Use fireworks to boost me.");
        add(Tier.HARD, "I'm easy to lift but hard to throw. What am I?", "feather",
                "I'm very light.", "Birds have me.", "Easy to lift.");
        add(Tier.HARD, "The more of me there is, the less you see. What am I?", "darkness|dark",
                "Light fights me.", "Torches beat me.", "The more of me, the less you see.");
        add(Tier.HARD, "What gets sharper the more you use it?", "brain|mind|your brain",
                "Use it.", "You think with me.", "Exercise me and I grow smarter.");
        add(Tier.HARD, "A farmer has 17 sheep and all but 9 run away. How many are left?", "9|nine",
                "Read carefully.", "'All but' means 'all except'.", "The answer is a number.");
        add(Tier.HARD, "Two fathers and two sons go fishing. They catch three fish and each gets one. How?",
                "three people|3 people|grandfather father and son|grandfather",
                "Count the people carefully.", "Some are two things at once.", "Grandfather, father, son.");
        add(Tier.HARD, "I can be long or short, grown or bought, painted or left bare. What am I?", "fingernails|nails|fingernail|nail",
                "You have ten of me.", "I'm cut and filed.", "I'm on your hands.");
        add(Tier.HARD, "In Minecraft I have hooves, you ride me with a saddle, and I come in many colors. What am I?", "horse",
                "I have hooves.", "You ride me.", "I come in many colors and I'm fast.");
    }
}
