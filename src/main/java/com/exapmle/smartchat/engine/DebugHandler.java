package com.example.smartchat.engine;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;

/** Shows what the engine understood when ai.debug is on. Never consumes the message. */
public final class DebugHandler implements ChatRouter.Handler {

    @Override
    public boolean handle(ChatContext ctx) {
        if (!EngineSettings.DEBUG.asBoolean()) return false;
        Intents intents = ctx.intents();
        ctx.reply("[debug] intent=" + intents.primary() + " request=" + intents.requestCue + " | " + intents.describe());
        ItemRequest request = ctx.itemRequest();
        ctx.reply("[debug] item request: " + (request == null ? "nothing found" : request));
        return false;
    }
}
