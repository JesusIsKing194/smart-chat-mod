package com.example.smartchat.eggs;

/**
 * Eight eggs whose actual features live elsewhere (the assistant, the games, the campfire stories).
 * They do nothing on their own: using the feature just marks the egg as discovered for that player,
 * so it shows up as a normal word instead of scrambled text in the How to Use menu.
 */
public final class ObserverEggs {

    private ObserverEggs() {}

    public static void register() {
        Egg.define("joke", "Tell Me a Joke", Egg.Category.ASSISTANT, "The assistant tells a joke, with a pause before the punchline.",
                "tell me a joke", "got any jokes", "make me laugh", "say something funny").observe();

        Egg.define("story", "Tell Me a Story", Egg.Category.ASSISTANT, "A short made-up story set in your biome.",
                "tell me a story", "tell a story", "short story").observe();

        Egg.define("riddle", "Riddle", Egg.Category.ASSISTANT, "A riddle game with hints and points.",
                "riddle", "give me a riddle", "tell me a riddle", "riddle me this").observe();

        Egg.define("eightball", "Magic 8 Ball", Egg.Category.ASSISTANT, "Ask the magic 8-ball any question.",
                "magic 8 ball", "8 ball", "eight ball").observe();

        Egg.define("rps", "Rock Paper Scissors", Egg.Category.ASSISTANT, "A best-of-five match against the assistant.",
                "rock paper scissors").observe();

        Egg.define("alive", "Are You Alive?", Egg.Category.ASSISTANT, "The assistant answers according to its mood.",
                "are you alive", "are you real", "are you sentient").observe();

        Egg.define("fortune", "Fortune", Egg.Category.ASSISTANT, "A fortune cookie with a lucky number and item.",
                "my fortune", "fortune cookie", "tell my fortune", "read my fortune").observe();

        Egg.define("campfire", "Campfire Stories", Egg.Category.ASSISTANT, "A scary choose-your-own-ending story by the fire.",
                "campfire", "campfire story", "campfire stories").observe();
    }
}
