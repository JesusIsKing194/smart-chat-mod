package com.example.smartchat;

import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.DebugHandler;
import com.example.smartchat.engine.EngineSettings;
import com.example.smartchat.items.ItemHandler;
import com.example.smartchat.items.ItemSettings;
import com.example.smartchat.stacked.StackedHandler;
import com.example.smartchat.stacked.StackedSettings;

/**
 * Central place where every feature is switched on.
 * Each batch adds its own lines here (settings are registered first, chat handlers second).
 */
public final class Features {

    private Features() {}

    public static void registerAll() {
        Settings.init();

        // Batch 2: smart text engine
        EngineSettings.init();
        ChatRouter.register(-100, "debug", new DebugHandler());

        // Batch 3: items and "get stacked"
        ItemSettings.init();
        StackedSettings.init();
        ChatRouter.register(50, "stacked", new StackedHandler());
        ChatRouter.register(100, "items", new ItemHandler());

        // --- Later batches add their register() calls below this line ---
    }
}
