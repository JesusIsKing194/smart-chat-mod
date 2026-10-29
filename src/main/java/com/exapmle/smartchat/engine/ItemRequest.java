package com.example.smartchat.engine;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** What the player asked for, already understood: item, amount, enchantments, name. */
public final class ItemRequest {

    /** level = EnchantIndex.LEVEL_MAX means "highest level". */
    public record EnchantWish(String id, int level) {}

    public Item item;
    public String itemKey = "";
    public int quantity = 1;
    public final List<EnchantWish> enchants = new ArrayList<>();
    public boolean book;
    public boolean fullyEnchanted;
    public String customName;
    /** Filled for "netherite set", "diamond armor", "iron tools" style requests (item stays null). */
    public final List<Item> kit = new ArrayList<>();
    public boolean ambiguous;
    public List<String> alternatives = new ArrayList<>();
    public double confidence = 1.0;

    public boolean isKit() {
        return !kit.isEmpty();
    }

    @Override
    public String toString() {
        String what = isKit() ? "kit(" + kit.size() + ")" : itemKey;
        return what + " x" + quantity + " ench=" + enchants + (book ? " book" : "") + (fullyEnchanted ? " full" : "")
                + (customName != null ? " name=" + customName : "")
                + (ambiguous ? " AMBIGUOUS" + alternatives : "") + " conf=" + String.format("%.2f", confidence);
    }
}
