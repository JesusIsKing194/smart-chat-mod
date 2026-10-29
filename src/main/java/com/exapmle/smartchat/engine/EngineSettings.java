package com.example.smartchat.engine;

import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;

/** Settings for the smart text engine. */
public final class EngineSettings {

    static {
        Settings.describeSection("ai", "Smart assistant");
    }

    public static final Setting DEBUG = Settings.bool("ai", "debug", false,
            "Shows what the assistant understood from each of your messages (great for testing). Turn off for normal play.");

    public static final Setting FUZZY_THRESHOLD = Settings.decimal("ai", "fuzzy_threshold", 0.78, 0.5, 1.0,
            "How forgiving typo matching is. Lower = guesses more, higher = stricter.");

    public static final Setting ASK_WHEN_UNSURE = Settings.bool("ai", "ask_when_unsure", true,
            "When a name could mean several things, the assistant asks which one instead of guessing.");

    public static final Setting MAX_ALTERNATIVES = Settings.integer("ai", "max_alternatives", 4, 1, 8,
            "How many 'did you mean' options the assistant shows.");

    private EngineSettings() {}

    public static void init() {}
}
