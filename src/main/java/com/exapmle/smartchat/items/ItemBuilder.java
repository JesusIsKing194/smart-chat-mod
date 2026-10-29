package com.example.smartchat.items;

import com.example.smartchat.engine.ItemRequest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Turns an understood ItemRequest into real item stacks (amount, enchantments, name). */
public final class ItemBuilder {

    private ItemBuilder() {}

    public static List<ItemStack> build(ServerLevel level, ItemRequest req) {
        List<ItemStack> out = new ArrayList<>();
        int amount = Math.min(Math.max(1, req.quantity), ItemSettings.MAX_AMOUNT.asInt());
        int maxStacks = ItemSettings.MAX_STACKS.asInt();
        List<Item> items = req.isKit() ? req.kit : (req.item == null ? List.<Item>of() : List.of(req.item));

        for (Item item : items) {
            int stackMax = Math.max(1, new ItemStack(item, 1).getMaxStackSize());
            int remaining = amount;
            while (remaining > 0 && out.size() < maxStacks) {
                int n = Math.min(remaining, stackMax);
                ItemStack stack = new ItemStack(item, n);
                decorate(level, stack, req);
                out.add(stack);
                remaining -= n;
            }
        }
        return out;
    }

    private static void decorate(ServerLevel level, ItemStack stack, ItemRequest req) {
        if (req.customName != null && !req.customName.isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(req.customName).withStyle(ChatFormatting.AQUA));
        }
        EnchantTables.apply(level, stack, req.enchants, req.fullyEnchanted, req.book);
    }
}
