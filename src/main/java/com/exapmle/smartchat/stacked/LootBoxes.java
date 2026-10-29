package com.example.smartchat.stacked;

import com.example.smartchat.config.Setting;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.items.EnchantTables;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** Builds the contents of the three "stacked" shulker boxes from the settings. */
public final class LootBoxes {

    public record Box(String boxItemId, List<ItemStack> contents) {}

    private static final String[] BOX_IDS = {"black_shulker_box", "purple_shulker_box", "light_blue_shulker_box"};

    private LootBoxes() {}

    public static List<Box> build(ServerLevel level) {
        Setting[] sources = {StackedSettings.BOX_1, StackedSettings.BOX_2, StackedSettings.BOX_3};
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            boxes.add(new Box(BOX_IDS[i], parse(level, sources[i].asList())));
        }
        return boxes;
    }

    private static List<ItemStack> parse(ServerLevel level, List<String> entries) {
        List<ItemStack> out = new ArrayList<>();
        for (String entry : entries) {
            String idPart = entry;
            int count = 1;
            int star = entry.indexOf('*');
            if (star > 0) {
                idPart = entry.substring(0, star).trim();
                try {
                    count = Math.max(1, Integer.parseInt(entry.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            if (idPart.contains(":")) idPart = idPart.substring(idPart.indexOf(':') + 1);
            Item item = ItemIndex.byPath(idPart);
            if (item == null) continue;
            int max = Math.max(1, new ItemStack(item, 1).getMaxStackSize());
            int left = count;
            while (left > 0 && out.size() < 27) {
                int n = Math.min(left, max);
                ItemStack stack = new ItemStack(item, n);
                EnchantTables.apply(level, stack, List.of(), true, false);
                out.add(stack);
                left -= n;
            }
        }
        return out;
    }

    /** The shulker box as an item with its contents inside (used when the box cannot be dropped). */
    public static ItemStack asItem(Box box) {
        Item item = ItemIndex.byPath(box.boxItemId());
        if (item == null) item = ItemIndex.byPath("shulker_box");
        ItemStack stack = new ItemStack(item, 1);
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack s : box.contents()) copies.add(s.copy());
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copies));
        return stack;
    }
}
