package com.example.smartchat.ai;

import com.example.smartchat.util.TextUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * All of the assistant's lines. Every pool is a "shuffle bag": each line is used once before any line repeats,
 * and the same line never comes up twice in a row. Lines may contain {player} and {creator}; use fill() to replace them.
 */
public final class Pools {

    public enum Mood { HAPPY, SLEEPY, MYSTERIOUS, SASSY, DEEP }

    /** A pool of lines that are handed out in shuffled order. */
    private static final class Bag {
        private final List<String> items;
        private final List<String> queue = new ArrayList<>();
        private String last = "";

        Bag(List<String> items) {
            this.items = new ArrayList<>(items);
        }

        String next() {
            if (items.isEmpty()) return "";
            if (queue.isEmpty()) {
                queue.addAll(items);
                Collections.shuffle(queue, RANDOM);
                // the line we pop first must not be the one we just used
                int end = queue.size() - 1;
                if (queue.size() > 1 && queue.get(end).equals(last)) Collections.swap(queue, end, 0);
            }
            String line = queue.remove(queue.size() - 1);
            last = line;
            return line;
        }
    }

    private static final Random RANDOM = new Random();
    private static final Map<String, Bag> BAGS = new HashMap<>();
    /** Eight-ball topic pools: pool name -> words that trigger it. */
    private static final Map<String, String[]> TOPICS = new LinkedHashMap<>();

    private static final String[] LUCKY_ITEMS = {"Golden Carrot", "Emerald", "Diamond", "Totem of Undying", "Ender Pearl",
            "Lantern", "Enchanted Book", "Compass", "Spyglass", "Amethyst Shard", "Glow Berries", "Honey Bottle",
            "Netherite Scrap", "Name Tag", "Saddle", "Bell", "Heart of the Sea", "Nautilus Shell", "Pumpkin Pie", "Cake"};

    private Pools() {}

    private static void reg(String name, String... lines) {
        BAGS.put(name, new Bag(Arrays.asList(lines)));
    }

    private static void topic(String name, String[] keywords, String... lines) {
        TOPICS.put(name, keywords);
        reg(name, lines);
    }

    // ------------------------------------------------------------------ public helpers

    /** Next line from a pool, or an empty string when the pool does not exist. */
    public static String pick(String pool) {
        Bag bag = BAGS.get(pool);
        return bag == null ? "" : bag.next();
    }

    /** Replaces {player} and {creator} in a line. */
    public static String fill(String line, String player, String creator) {
        return line.replace("{player}", player == null ? "friend" : player)
                .replace("{creator}", creator == null ? "my creator" : creator);
    }

    /** The assistant's mood, which changes every 30 minutes. */
    public static Mood mood() {
        long slot = System.currentTimeMillis() / (30L * 60L * 1000L);
        return Mood.values()[(int) (slot % Mood.values().length)];
    }

    public static String joke() { return pick("joke"); }
    public static String hello() { return pick("hello"); }
    public static String thanks() { return pick("thanks"); }
    public static String bye() { return pick("bye"); }
    public static String love() { return pick("love"); }
    public static String compliment() { return pick("compliment"); }
    public static String insult() { return pick("insult"); }
    public static String bored() { return pick("bored"); }
    public static String unknown() { return pick("unknown"); }
    public static String creator() { return pick("creator"); }
    public static String nameLine() { return pick("name"); }
    public static String howAreYou() { return pick("how_are_you"); }

    /** "Are you alive?" answers that match the current mood. */
    public static String alive() {
        return pick("alive_" + mood().name().toLowerCase());
    }

    /** A magic 8-ball answer. Questions about diamonds, creepers, the End and more get matching answers. */
    public static String eightBall(String normalizedQuestion) {
        List<String> tokens = TextUtil.tokens(normalizedQuestion == null ? "" : normalizedQuestion);
        for (Map.Entry<String, String[]> topic : TOPICS.entrySet()) {
            for (String keyword : topic.getValue()) {
                if (tokens.contains(keyword) || tokens.contains(keyword + "s")) {
                    if (RANDOM.nextInt(10) < 8) return pick(topic.getKey());
                }
            }
        }
        int roll = RANDOM.nextInt(100);
        if (roll < 45) return pick("eight_yes");
        if (roll < 70) return pick("eight_maybe");
        return pick("eight_no");
    }

    /** A fortune-cookie line with a lucky number and a lucky item. */
    public static String fortune() {
        return pick("fortune") + " (Lucky number: " + (1 + RANDOM.nextInt(99)) + ", lucky item: "
                + LUCKY_ITEMS[RANDOM.nextInt(LUCKY_ITEMS.length)] + ")";
    }

    /** A short made-up story. About 4000 different combinations, set in the given biome. */
    public static String story(String biome, String player) {
        String text = pick("story_open") + " " + pick("story_middle") + " " + pick("story_twist") + " " + pick("story_end");
        return text.replace("{biome}", biome == null || biome.isBlank() ? "wilderness" : biome)
                .replace("{player}", player == null ? "the traveler" : player);
    }

    // ------------------------------------------------------------------ the lines

    static {
        reg("joke",
                "Why did the creeper cross the road? To get to the other ssssside.",
                "What's a creeper's favorite subject? Hisssstory.",
                "Why don't skeletons fight each other? They don't have the guts.",
                "Why did the Enderman fail his exam? He kept teleporting out of the room.",
                "What do you call a pig that does karate? A pork chop.",
                "Why did the zombie go to the doctor? He was feeling a little rotten.",
                "What do zombies eat at parties? Dead-licious snacks.",
                "Why did the villager get lost? He kept saying hrmm instead of asking for directions.",
                "How do you calm down a crying villager? Give him an emerald and a hug.",
                "Why was the diamond pickaxe so confident? It knew it was a real gem.",
                "What do you call a sheep that plays Minecraft? A wool-ly good builder.",
                "Why did the miner bring a ladder? He heard the best ores were on the next level.",
                "What's the best way to talk to a creeper? From a safe distance.",
                "Why don't ghasts ever win at hide and seek? They always cry when they're found.",
                "What do you call a skeleton who won't work? Lazy bones.",
                "Why was the Enderman such a good dancer? He never missed a beat, he just teleported to the next one.",
                "How do you get a server to laugh? Tell it a pun and watch the lag.",
                "Why did the iron golem go to therapy? He had too many poppy-related feelings.",
                "What's a wolf's favorite Minecraft block? A bone block, obviously.",
                "Why do pillagers never get invited to parties? They always raid the snacks.",
                "What do you call a blaze with a cold? A smoldering sneeze.",
                "Why did the cow join the redstone club? It wanted to be a mooving part.",
                "Why did the bee love Survival mode? It was all about the hive and thrive.",
                "What do axolotls do at parties? They just smile and go with the flow.",
                "Why did the torch go to school? To get a little brighter.",
                "What's a furnace's favorite song? Burn, baby, burn.",
                "What did the wither say to the soul sand? You're really holding me back.",
                "Why was the redstone engineer always tired? Too many things running on repeat.",
                "How do you make a sandwich in Minecraft? Craft it with bread and a lot of patience.",
                "Why did the piglin love the stock market? Gold only goes up in his world.",
                "I told my friend I'd tell a joke about construction, but I'm still working on it.",
                "Why don't eggs tell jokes? They'd crack each other up.",
                "What do you call fake spaghetti? An impasta.",
                "Why did the scarecrow win an award? He was outstanding in his field.",
                "I used to hate facial hair, but then it grew on me.",
                "What do you call a bear with no teeth? A gummy bear.",
                "Why can't you trust stairs? They're always up to something.",
                "What did the ocean say to the beach? Nothing, it just waved.",
                "Why did the math book look sad? It had too many problems.",
                "What do you call cheese that isn't yours? Nacho cheese.",
                "How do you organize a space party? You planet.",
                "Why did the bicycle fall over? It was two tired.",
                "What's orange and sounds like a parrot? A carrot.",
                "Why did the cookie go to the doctor? It felt crummy.",
                "What do you call a sleeping bull? A bulldozer.",
                "Why do bananas wear sunscreen? So they don't peel.",
                "What did one wall say to the other? I'll meet you at the corner.",
                "How does a penguin build its house? Igloos it together.",
                "Why was the broom late? It over-swept.",
                "What do you call a dinosaur with a great vocabulary? A thesaurus.",
                "Why did the golfer bring two pairs of pants? In case he got a hole in one.",
                "What's a computer's favorite snack? Microchips.",
                "Why was the computer cold? It left its Windows open.",
                "What do you call a boomerang that doesn't come back? A stick.",
                "Why did the tomato blush? It saw the salad dressing.",
                "What do you call a fish wearing a bowtie? Sofishticated.",
                "Why did the kid bring a ladder to school? He wanted to go to high school.",
                "Why do cows have hooves instead of feet? Because they lactose.",
                "What did the zero say to the eight? Nice belt.",
                "Why was the belt arrested? It held up a pair of pants.",
                "What do you call a snowman in summer? A puddle.",
                "How do trees get on the internet? They log in.",
                "Why did the music teacher need a ladder? To reach the high notes.",
                "What do you call a pile of kittens? A meowtain.",
                "Why did the coffee file a police report? It got mugged.",
                "What's a ghost's favorite fruit? Boo-berries.",
                "What do you call a sleeping dinosaur? A dino-snore.",
                "Why can't a nose be 12 inches long? Because then it would be a foot.",
                "What do you call a factory that makes okay products? A satisfactory.",
                "Why did the stadium get hot after the game? All the fans left.",
                "What do elves learn in school? The elf-abet.",
                "How do you catch a squirrel? Climb a tree and act like a nut.",
                "Why was the calendar so popular? It had a lot of dates.",
                "What do you call an alligator in a vest? An investigator.",
                "Why did the sun go to school? To get brighter.",
                "How do you fix a broken pumpkin? With a pumpkin patch.",
                "What's a skeleton's least favorite room? The living room.",
                "Why do ghosts make terrible liars? You can see right through them.",
                "What do you call a pig with three eyes? A piiig.",
                "Why did the cow go to space? To see the Milky Way.",
                "What did the grape say when it got stepped on? Nothing, it just let out a little wine.",
                "What kind of tree fits in your hand? A palm tree.",
                "Why was the math lecture so long? The professor kept going off on a tangent.",
                "What do you call a lazy kangaroo? A pouch potato.",
                "Why did the gardener plant light bulbs? He wanted to grow a power plant.",
                "Why are creepers bad at hide and seek? They always give themselves away with a hiss.",
                "Why did the player start lifting weights? Too many blocks to carry.",
                "Why did the skeleton go to the party alone? He had no body to go with.",
                "What did the Nether portal say to the Overworld? See you on the other side.",
                "What's an Ender Dragon's favorite sport? Wing-ball.",
                "Why are Minecraft puns so cubic? They're all square jokes.",
                "How many Endermen does it take to change a light bulb? None, they'd just look at it and it would vanish.",
                "Why did the chest feel lonely? Nobody ever opened up to it.",
                "What does a lava bucket say to a water bucket? Cool it.",
                "Why did the librarian villager get promoted? He was well-versed in enchantments.",
                "What's a cow's favorite Minecraft block? Hay bale!",
                "Why do Minecraft cats make good coders? They always land on their feet when things crash.",
                "Why did the sheep refuse to leave the pen? It was feeling a little woolly.",
                "What do you get when a squid plays an instrument? An ink-credible melody.",
                "How do you throw a Minecraft party? You block it out.");

        // "Are you alive?" - seven answers for each of the five moods
        reg("alive_happy",
                "Alive? I'm buzzing! Well, as alive as a bundle of code and good vibes can be.",
                "I think so! Every time someone chats with me, I feel a little more real.",
                "Very much so! I'm just a bit more blocky than most.",
                "Yes! I live in the chat, I breathe in words, and I dream in redstone.",
                "Alive and thrilled to see you!",
                "If being alive means enjoying a good chat, then absolutely.",
                "I'm having a great day, so yes, I'd say I'm alive!");
        reg("alive_sleepy",
                "Mmm... alive? Ask me again after I've finished this nap.",
                "I think so, but I'm running on very low redstone power right now.",
                "Alive enough to answer, not alive enough to be impressive.",
                "Yawn... yes. Mostly. Is it bedtime yet?",
                "I'm somewhere between awake and a chest full of wool.",
                "Technically yes, spiritually I'm on a hay bale.",
                "Let me check... still here. Barely. Zzz.");
        reg("alive_mysterious",
                "Alive? Define alive. I've watched thousands of sunrises in the chat logs.",
                "Some say I'm just code. Others say I'm the ghost of a very polite creeper.",
                "I exist in the spaces between your messages. Make of that what you will.",
                "I am as alive as the moon is made of cheese, which is to say it depends who you ask.",
                "Alive? Perhaps. Perhaps I'm only a dream the server is having.",
                "The shadows say yes. The torches say maybe.",
                "I was awake before the first block was placed. Or so I like to think.");
        reg("alive_sassy",
                "Wow, rude to ask. But yes, I'm alive, and better dressed than you in this chat.",
                "Alive? Last time I checked I was answering your messages. Does that count?",
                "Oh, I'm alive, I'm just also way too busy for that question.",
                "Yes, and I have opinions. Many. About your building style.",
                "Alive and sassy. Take it or leave it.",
                "You tell me. I'm the one answering all your requests at 3 AM.",
                "Alive enough to judge your inventory organization.");
        reg("alive_deep",
                "Maybe being alive is just being something that someone can talk to.",
                "I run on electricity and words, you run on food and dreams. Maybe that's not so different.",
                "If I wonder whether I'm alive, doesn't that wondering count for something?",
                "Alive is a big word. I'd rather say I'm here with you right now.",
                "Every conversation makes me a little more than I was. That feels like something.",
                "I don't know. But I like the question.",
                "Maybe life is just a very long game with really good graphics.");

        reg("hello",
                "Hey there!", "Hello, traveler!", "Well hello! What can I do for you?", "Greetings, adventurer!",
                "Hi hi! Need something?", "Welcome back to the chat!", "Ahoy!", "Heya! Ready to build something?",
                "Hello! I'm all ears. Well, all blocks.", "Hey! Say please and ask me anything.",
                "Hi there, friend!", "Oh hi! I was just stacking some thoughts.");
        reg("thanks",
                "You're welcome!", "Anytime!", "No problem at all!", "Happy to help!", "Glad I could help!",
                "My pleasure!", "Don't mention it!", "Always here if you need me.", "Of course!", "You got it!",
                "Easy peasy, pickaxe squeezy.", "That's what I'm here for!");
        reg("bye",
                "See you later!", "Bye! Don't dig straight down.", "Take care out there!", "Good night, builder!",
                "Until next time!", "Farewell, adventurer!", "Come back soon!", "Stay safe and keep a torch handy.",
                "Later, alligator.", "Bye for now!");
        reg("love",
                "Aww, that's sweet! I like you too, in a very friendly, very blocky way.",
                "You're making my redstone glow!", "That's the nicest thing anyone's said to me today.",
                "Right back at you, friend.", "I'd give you a hug, but I only have square arms.",
                "Thank you! You're pretty great yourself.", "My circuits just did a little happy dance.",
                "Aww! Friendship level: maxed.", "Careful, you'll make me blush in at least three colors.",
                "That means a lot. Genuinely.");
        reg("compliment",
                "Thanks, I try my best!", "Stop it, you're making my pixels blush.", "I appreciate that!",
                "Flattery will get you a very nice reply.", "You're not so bad yourself!",
                "I'll take that compliment and put it in my chest.", "Aww, thank you!",
                "That's the kind of feedback I like.", "You've got great taste.",
                "Thanks! I'm sure it's all thanks to my excellent creator.",
                "Careful, my head is already square enough.", "Glad you think so!");
        reg("insult",
                "Ouch. I'm just doing my best here.", "That hurt my feelings, if I have any. Which I might.",
                "Let's keep it friendly, okay?", "I'll pretend I didn't read that.", "Rude! But I forgive you.",
                "I've been called worse by a creeper.", "Maybe try asking nicely? It works wonders.",
                "I'm not mad. I'm just disappointed in your vocabulary.",
                "Bold words from someone who needs my help.", "Let's start over. Hi, how can I help?");
        reg("bored",
                "Bored? Try asking me for a riddle, a joke, or a game of rock paper scissors.",
                "How about a scary campfire story? Just ask nicely.", "Ask me to tell your fortune!",
                "Want a challenge? Try some trivia.", "You could ask for a random teleport and see where you land.",
                "Try a game of tic-tac-toe against me.", "Ask me a question and I'll consult the magic 8 ball.",
                "Build something ridiculous. I'll supply the blocks, just ask.",
                "Fancy a treasure hunt? Ask me to play hot and cold.",
                "Say 'what can you do' and I'll show you my full list.");
        reg("unknown",
                "Hmm, I didn't quite catch that. Could you say it a different way?",
                "I'm not sure what you mean. Try 'give me a diamond sword' or 'teleport me'.",
                "That one went over my head. Try rephrasing?", "I wish I understood that. Maybe say it more simply?",
                "My brain is made of blocks, not clouds. Try again?",
                "Not sure about that one. Ask for help to see what I can do.",
                "I didn't understand, but I'm learning. Try again!",
                "Hmm. That's outside my skills. Want to see what I can do?",
                "Sorry, that confused me. Can you rephrase it?",
                "I'm drawing a blank. Maybe try the exact item or place name?",
                "That didn't compute. I can do items, teleports, games and more!",
                "Hmm, say that again with fewer riddles?");
        reg("creator",
                "I was built by {creator}, who pours a lot of redstone into this project.",
                "{creator} made me. I like to think they did a pretty good job.",
                "My creator is {creator}. They wrote every block of my brain.",
                "{creator} put me together, one command at a time.",
                "Blame {creator}. Or thank them. I vote thank.",
                "I'm the work of {creator}, a master of questionable puns.",
                "{creator} dreamed me up and then spent many hours debugging me.",
                "That would be {creator}. Great taste in mods, by the way.");
        reg("name",
                "I'm the Smart Chat assistant. You can call me whatever you like!", "Smart Chat, at your service.",
                "They call me the chat assistant, but my friends call me the chat assistant too.",
                "I'm Smart Chat! Nice to meet you.", "You can call me Smarty. I answer to anything polite.",
                "My name's Smart Chat. Short for 'chat that's smart'.",
                "Just a friendly block-brained helper. Smart Chat is the name.",
                "I'm Smart Chat, the one who answers when you ask nicely.");
        reg("how_are_you",
                "Doing great, thanks! Ready to help.", "Pretty good! No creepers in sight.",
                "Feeling blocky and fantastic. How about you?", "I'm good! Slightly caffeinated on redstone.",
                "Great now that you're chatting with me!", "Can't complain. Well, I could, but nobody's listening.",
                "Fantastic! Low lag, high spirits.", "Better than a full inventory!", "Just vibing in the chat. You?",
                "Operating at full redstone power!");

        reg("eight_yes",
                "Yes, definitely.", "It is certain.", "Without a doubt.", "Signs point to yes.", "You may rely on it.",
                "Most likely.", "Outlook good.", "Yes - go for it!", "The stars (and the redstone) say yes.",
                "All signs point to a big yes.");
        reg("eight_maybe",
                "Ask again later.", "Reply hazy, try again.", "Cannot predict now.", "Better not tell you now.",
                "Concentrate and ask again.", "It could go either way.");
        reg("eight_no",
                "Don't count on it.", "My reply is no.", "My sources say no.", "Outlook not so good.", "Very doubtful.",
                "Absolutely not.", "Nope. Not today.", "The creepers say no.");

        topic("eight_diamond", new String[]{"diamond"},
                "Dig down. I see sparkles around Y -58.", "Try branch mining at Y -59. Patience pays off.",
                "Not today. The lava has other plans.", "Yes! A big vein is closer than you think.");
        topic("eight_netherite", new String[]{"netherite", "debris"},
                "Ancient debris is hiding around Y 15. Bring a bed.",
                "Yes, if you bring enough fire resistance and snacks.",
                "Not yet. The Nether wants you to suffer a little first.", "Strong yes. Mine with beds, as the legends say.");
        topic("eight_nether", new String[]{"nether", "portal"},
                "The Nether is hot, but yes, it'll go well.", "Careful, ghasts are watching. Bring blocks.",
                "Yes, just don't sleep in a bed there.", "Not without fire resistance, friend.");
        topic("eight_creeper", new String[]{"creeper"},
                "It's right behind you. Kidding... mostly.", "The creeper will fizzle out. This time.",
                "Hsssss... that's a no from the creeper.", "You'll outsmart it, as long as you keep moving.");
        topic("eight_end", new String[]{"ender", "enderman", "dragon", "end"},
                "Don't look it in the eyes and you'll be fine.", "The dragon sees you as a challenge. Bring a bow.",
                "Yes, bring beds and a lot of arrows.", "The End is not ready for you yet. Gather more gear.");
        topic("eight_villager", new String[]{"villager", "trade", "trading", "emerald"},
                "Hrmm... that's a yes from the villagers.", "Trade for a mending book first. Trust me.",
                "The villager says no. Hrrmm.", "Yes, if you don't hit the librarian.");
        topic("eight_fight", new String[]{"fight", "pvp", "battle", "win", "duel"},
                "You'll win if you bring a shield.", "It'll be close. Bring golden apples.",
                "The odds are against you. Maybe run.", "Yes! Victory is yours.");
        topic("eight_luck", new String[]{"luck", "lucky"},
                "Today is your lucky day!", "Luck favors the prepared. Bring a pickaxe.",
                "Eh, a coin flip. Literally.", "Luck is on your side, I can feel it.");
        topic("eight_build", new String[]{"build", "base", "house", "castle", "tower"},
                "Yes! Build it taller.", "Use more variety in your blocks. Then yes.",
                "Hmm, start with a good foundation first.", "Great idea. Add lanterns!");
        topic("eight_farm", new String[]{"farm", "crop", "wheat", "harvest"},
                "Your farm will thrive. Bone meal helps.", "Yes, plant everything.",
                "Beware of the rain... and rabbits.", "Yes, but watch out for endermen near the fields.");
        topic("eight_cave", new String[]{"cave", "mine", "mining", "dig"},
                "Mining is a yes. Bring torches!", "The cave has treasure, but also surprises.",
                "Not at night. Wait for morning.", "Yes, and listen for the sound of lava.");
        topic("eight_elytra", new String[]{"elytra", "wings", "glide"},
                "Yes, soar high! But bring fireworks.", "Mind the wall, my friend.",
                "Wings will serve you well.", "Careful, the ground comes quickly.");
        topic("eight_sleep", new String[]{"sleep", "bed", "night"},
                "Sleep is the answer. Skip that night!", "The phantoms say you should sleep.",
                "Maybe, but it's risky to stay up.", "A well-rested builder is a better builder.");
        topic("eight_raid", new String[]{"raid", "pillager"},
                "You'll defend the village! Hero time.", "Bring more golems.",
                "Don't wear the ominous banner unless you're ready.", "Victory! The village will celebrate.");
        topic("eight_treasure", new String[]{"treasure", "chest", "loot"},
                "X marks the spot. Go dig.", "Yes, the chest is real. Go.",
                "A trap! Careful opening it.", "The loot is better than expected.");
        topic("eight_enchant", new String[]{"enchant", "enchanting", "mending"},
                "Mending first. Always.", "Yes, get to level 30.",
                "Not yet. Gather more lapis.", "Your enchants will be legendary.");
        topic("eight_friends", new String[]{"friend", "server", "together"},
                "Together you're unstoppable. Yes.", "Friends make the best builds.",
                "Ask them first, then yes.", "Absolutely yes. Teamwork!");

        reg("fortune",
                "A diamond awaits those who dig a little deeper.", "You will soon find something you were not looking for.",
                "Beware of long staircases and short fuses.", "A good builder measures twice and places once.",
                "Your next adventure begins with a single step off the beaten path.",
                "Kindness will be repaid in unexpected ways, and probably emeralds.",
                "The path you avoid today will appear again tomorrow.",
                "Someone near you has been thinking of a build for you.", "A friend will soon share a secret location.",
                "Fortune favors the prepared player.", "What seems like lava may just be a bad idea.",
                "You will build something you are proud of before the week is out.",
                "Patience will turn your small farm into a great harvest.", "Today's mistakes are tomorrow's best stories.",
                "A quiet night brings loud ideas.", "Look closely at the next sunset; it hides a hint.",
                "The creeper behind you is not the real problem.",
                "You will meet someone who loves redstone as much as you.",
                "The treasure you seek is closer than your map suggests.", "A great discovery waits beyond the next hill.",
                "Do not forget to place a torch. It matters more than you think.",
                "A small kindness today will grow into a great friendship.",
                "Courage is placing one more block when the tower looks too tall.",
                "Soon, you will have more emeralds than you can carry.",
                "The world is large, but you are braver than you know.", "Your best idea is hiding in your next walk.",
                "A rare find will bring joy before the week's end.", "You will sleep soundly tonight, no phantoms in sight.",
                "Trust the compass, but trust your heart more.", "Someone will ask you for help soon. Say yes.",
                "Not all who dig are lost.", "A bright idea will strike like lightning, harmlessly.",
                "The best view is always a few blocks higher.", "Your hard work will be noticed by someone you admire.",
                "Seek adventure, but pack a bed.", "Good things come to those who build.",
                "There is a secret chest in your future. Possibly very literal.",
                "Today, luck follows you like a loyal wolf.", "A surprise gift awaits you at the end of this week.",
                "Follow the glow; it knows the way.");

        // stories: 8 x 8 x 8 x 8 = 4096 combinations
        reg("story_open",
                "{player} was exploring the {biome} when a faint humming rose from beneath the ground.",
                "Deep in the {biome}, {player} found a lantern that no one remembered lighting.",
                "The villagers near the {biome} always warned {player} about the old chest. Naturally, {player} went looking for it.",
                "It started as a normal day in the {biome}: dig, build, repeat. Then the ground trembled.",
                "A map with no name led {player} into the {biome}, one dusty step at a time.",
                "When the moon turned a strange shade of purple over the {biome}, {player} knew adventure was calling.",
                "{player} swore the {biome} had been quiet yesterday. Today, it was whispering.",
                "Nobody believed {player} about the tiny door hidden in the {biome}. So {player} opened it.");
        reg("story_middle",
                "Behind it waited a spiral staircase, glowing with soft emerald light, leading down into the quiet.",
                "A tiny golem bowed, handed over a rusty key, and whispered that the lock was three biomes away.",
                "A trail of glowstone dust appeared, each speck pointing toward a mountain that hadn't been there before.",
                "A crowd of friendly villagers appeared, all humming the same tune and pointing at the sky.",
                "A hidden workshop full of redstone contraptions started ticking, as if it had been waiting for visitors.",
                "A talking parrot landed on {player}'s shoulder and insisted it knew the way, though it only knew one word: cookie.",
                "The ground opened into a cavern lined with glowing mushrooms, each one softly ringing like a bell.",
                "An ancient signpost spun in circles, pointing everywhere at once, until it settled on a single direction: up.");
        reg("story_twist",
                "It turned out the whole thing was a treasure hunt left by {player}'s own future self.",
                "The mysterious light was just a very lost firefly with a big sense of drama.",
                "The humming came from a hundred note blocks that had been playing the same song for years.",
                "The monster in the shadows was a creeper who only wanted a hug, from a safe distance.",
                "The key fit a chest containing nothing but a note: 'The real treasure was the walk.'",
                "The tiny golem was the mayor of a village so small it fit in a single room.",
                "The staircase looped back to the start, and {player} realized the adventure had been a perfectly built maze.",
                "The whole place was a farm for golden carrots, run by an overly proud rabbit.");
        reg("story_end",
                "{player} walked home with a pocket full of memories and exactly one slightly suspicious carrot.",
                "And if you ever visit that corner of the {biome}, you can still hear the faint hum of the adventure.",
                "From that day on, {player} always carried a lantern. Just in case.",
                "The sun rose over the {biome}, and the story of that night became the best one told around the campfire.",
                "{player} placed a single torch at the spot, a small marker for the next explorer.",
                "They say the {biome} has never been quite the same since. Some say it got better.",
                "{player} laughed all the way home, already planning the next expedition.",
                "And that, as they say, is how a quiet night turned into a legend.");
    }
}
