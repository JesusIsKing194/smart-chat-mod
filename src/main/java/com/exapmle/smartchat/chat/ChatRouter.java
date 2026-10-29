package com.example.smartchat.chat;

import com.example.smartchat.SmartChatSpawnerMod;
import com.example.smartchat.config.Settings;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every chat message goes through here. Handlers are tried in priority order (lowest number first);
 * the first handler that returns true "consumes" the message. A crashing handler never breaks the server.
 */
public final class ChatRouter {

    public interface Handler {
        boolean handle(ChatContext ctx) throws Exception;
    }

    private record Entry(int priority, String name, Handler handler) {}

    private static final List<Entry> HANDLERS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_HANDLED = new HashMap<>();

    private ChatRouter() {}

    public static void register(int priority, String name, Handler handler) {
        HANDLERS.add(new Entry(priority, name, handler));
        HANDLERS.sort(Comparator.comparingInt(Entry::priority));
    }

    public static void handle(ServerPlayer player, String raw) {
        if (!Settings.ENABLED.asBoolean()) return;
        if (raw == null || raw.isBlank() || raw.length() > Settings.MAX_MESSAGE_LENGTH.asInt()) return;

        long now = System.currentTimeMillis();
        Long last = LAST_HANDLED.get(player.getUUID());
        if (last != null && now - last < Settings.CHAT_COOLDOWN_MS.asInt()) return;

        ChatContext ctx = new ChatContext(player, raw);
        for (Entry entry : HANDLERS) {
            try {
                if (entry.handler().handle(ctx)) {
                    LAST_HANDLED.put(player.getUUID(), now);
                    return;
                }
            } catch (Throwable t) {
                SmartChatSpawnerMod.LOGGER.error("Chat handler '{}' failed", entry.name(), t);
            }
        }
    }
}
