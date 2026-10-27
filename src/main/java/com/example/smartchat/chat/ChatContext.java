package com.example.smartchat.chat;

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

    public void reply(String message) {
        Messages.say(player, message);
    }
}
