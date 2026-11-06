package com.example.smartchat.gui;

import com.example.smartchat.engine.ItemIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds chest-style menus that only the server knows about (nobody needs to install anything).
 * Icons can be clicked, but nothing can ever be taken out, moved or dropped.
 */
public final class MenuUtil {

    @FunctionalInterface
    public interface Click {
        /** button 0 = left click, 1 = right click */
        void on(ServerPlayer player, int button);
    }

    private MenuUtil() {}

    // ------------------------------------------------------------------ icons

    private static Component text(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(Style.EMPTY.withItalic(false).withColor(color));
    }

    /** An item with a coloured name and grey description lines. Unknown item ids become paper. */
    public static ItemStack icon(String itemPath, String name, ChatFormatting color, String... lore) {
        Item item = ItemIndex.byPath(itemPath);
        ItemStack stack = new ItemStack(item == null ? Items.PAPER : item, 1);
        stack.set(DataComponents.CUSTOM_NAME, text(name, color));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(text(line, ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    /** An icon whose name is scrambled, flickering text (used for undiscovered easter eggs). */
    public static ItemStack hiddenIcon(String itemPath, String name, String... lore) {
        ItemStack stack = icon(itemPath, name, ChatFormatting.WHITE, lore);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(Style.EMPTY.withItalic(false).withObfuscated(true)));
        return stack;
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        return stack;
    }

    // ------------------------------------------------------------------ menus

    public static Menu menu(String title, int rows) {
        return new Menu(title, Math.max(1, Math.min(6, rows)));
    }

    public static final class Menu {
        private final String title;
        private final int rows;
        private final ItemStack[] items;
        private final Click[] clicks;

        private Menu(String title, int rows) {
            this.title = title;
            this.rows = rows;
            this.items = new ItemStack[rows * 9];
            this.clicks = new Click[rows * 9];
        }

        public int size() {
            return items.length;
        }

        public Menu set(int slot, ItemStack stack) {
            return set(slot, stack, null);
        }

        public Menu set(int slot, ItemStack stack, Click click) {
            if (slot >= 0 && slot < items.length) {
                items[slot] = stack;
                clicks[slot] = click;
            }
            return this;
        }

        /** Fills every empty slot with a blank glass pane so the menu looks tidy. */
        public Menu fillEmpty() {
            for (int i = 0; i < items.length; i++) {
                if (items[i] == null) items[i] = filler();
            }
            return this;
        }

        public void open(ServerPlayer player) {
            SimpleContainer container = new SimpleContainer(items.length);
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) container.setItem(i, items[i].copy());
            }
            MenuType<?> type = switch (rows) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                default -> MenuType.GENERIC_9x6;
            };
            Click[] handlers = clicks;
            int menuRows = rows;
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new LockedMenu(type, containerId, inventory, container, menuRows, handlers),
                    Component.literal(title)));
        }
    }

    /** A chest menu where clicks run our code and never move any items. */
    private static final class LockedMenu extends ChestMenu {
        private final Click[] handlers;
        private final int slotCount;

        LockedMenu(MenuType<?> type, int containerId, Inventory inventory, Container container, int rows, Click[] handlers) {
            super(type, containerId, inventory, container, rows);
            this.handlers = handlers;
            this.slotCount = rows * 9;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (slotId >= 0 && slotId < slotCount && handlers[slotId] != null
                        && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                    handlers[slotId].on(serverPlayer, button);
                }
                // put everything back the way the server sees it, so nothing can be picked up or dragged
                this.sendAllDataToRemote();
            }
        }
    }
}
