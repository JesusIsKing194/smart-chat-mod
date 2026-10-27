package com.example.smartchat;

import com.example.smartchat.config.Settings;

/**
 * Central place where every feature is switched on.
 * Each later batch adds its own lines here (settings are registered first, chat handlers second).
 */
public final class Features {

    private Features() {}

    public static void registerAll() {
        Settings.init();
        // --- Later batches add their register() calls below this line ---
    }
}
