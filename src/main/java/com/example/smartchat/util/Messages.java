package com.example.smartchat.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Consistent chat messages from the mod's assistant. */
public final class Messages {

    private Messages() {}

    public static MutableComponent prefix() {
        return Component.literal("[Smart Chat] ").withStyle(ChatFormatting.AQUA);
    }

    public static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }

    public static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(prefix().append(message));
    }

    public static void warn(ServerPlayer player, String message) {
        player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
    }

    public static void actionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
