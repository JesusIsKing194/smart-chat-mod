package com.example.smartchat.chat;

import com.example.smartchat.engine.IntentEngine;
import com.example.smartchat.engine.Intents;
import com.example.smartchat.engine.ItemRequest;
import com.example.smartchat.engine.ItemRequestParser;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.TextUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Everything a chat handler needs to know about one chat message. */
public final class ChatContext {

    public final ServerPlayer player;
    public final ServerLevel level;
    /** The message exactly as the player typed it. */
    public final String raw;
    /** Normalized text: lower-case, letters and digits only, single spaces. */
    public final String text;
    public final List<String> tokens;
    public final boolean polite;

    private Intents intents;
    private ItemRequest itemRequest;
    private boolean itemParsed;

    public ChatContext(ServerPlayer player, String raw) {
        this.player = player;
        this.level = (ServerLevel) player.level();
        this.raw = raw;
        this.text = TextUtil.normalize(raw);
        this.tokens = TextUtil.tokens(this.text);
        this.polite = TextUtil.isPolite(this.text);
    }

    public boolean has(String phrase) {
        return TextUtil.containsPhrase(text, phrase);
    }

    public boolean hasAny(String... phrases) {
        return TextUtil.containsAny(text, phrases);
    }

    /** What the player seems to want (worked out once, then remembered). */
    public Intents intents() {
        if (intents == null) intents = IntentEngine.classify(this);
        return intents;
    }

    /** The message understood as an item request, or null when no item was found. */
    public ItemRequest itemRequest() {
        if (!itemParsed) {
            itemRequest = ItemRequestParser.parse(this);
            itemParsed = true;
        }
        return itemRequest;
    }

    public void reply(String message) {
        Messages.say(player, message);
    }
}
