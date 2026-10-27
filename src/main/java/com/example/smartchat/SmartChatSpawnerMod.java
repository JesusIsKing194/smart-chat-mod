package com.example.smartchat;

import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.ConfigManager;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartChatSpawnerMod implements ModInitializer {

    public static final String MOD_ID = "smartchatmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("SmartChat");

    @Override
    public void onInitialize() {
        // Features register their settings and chat handlers first, then the config is loaded on top.
        Features.registerAll();
        ConfigManager.load();
        PlayerDataStore.load();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            ConfigManager.tick();
            PlayerDataStore.tick();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PlayerDataStore.save());

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.decoratedContent().getString();
            MinecraftServer server = sender.level().getServer();
            if (server == null) return;
            server.execute(() -> ChatRouter.handle(sender, text));
        });

        LOGGER.info("Smart Chat Spawner Mod loaded.");
    }
}
