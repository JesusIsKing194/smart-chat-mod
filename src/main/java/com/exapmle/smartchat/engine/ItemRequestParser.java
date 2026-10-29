package com.example.smartchat.engine;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.util.NumberWords;
import com.example.smartchat.util.TextUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a chat message into an ItemRequest.
 * Enchantments can come before or after the item, numbers right after an enchantment are its level,
 * and only the other number is the amount.
 */
public final class ItemRequestParser {

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?i)\\b(named|called|titled)\\s+(.+?)(?=\\s+(?:please|plz|pls|and|with|thanks)\\b|[.!?]*$)");

    private static final String[] FULL_PHRASES = {
            "max enchants", "max enchanted", "maxed out", "maxed", "fully enchanted", "fully enchant",
            "all enchants", "all enchantments", "every enchant", "best enchants", "max enchantments"};

    private static final Map<String, String> MATERIALS = new HashMap<>();
    private static final Set<String> KIT_WORDS = new HashSet<>(Arrays.asList(
            "set", "armor", "armour", "full", "gear", "kit", "tools", "tool"));
    private static final String[] ARMOR = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] TOOLS = {"sword", "pickaxe", "axe", "shovel", "hoe"};

    static {
        MATERIALS.put("netherite", "netherite");
        MATERIALS.put("diamond", "diamond");
        MATERIALS.put("iron", "iron");
        MATERIALS.put("gold", "golden");
        MATERIALS.put("golden", "golden");
        MATERIALS.put("chainmail", "chainmail");
        MATERIALS.put("chain", "chainmail");
        MATERIALS.put("leather", "leather");
        MATERIALS.put("stone", "stone");
        MATERIALS.put("wooden", "wooden");
        MATERIALS.put("wood", "wooden");
        MATERIALS.put("copper", "copper");
    }

    private ItemRequestParser() {}

    /** Returns null when no item could be found. */
    public static ItemRequest parse(ChatContext ctx) {
        List<String> tokens = new ArrayList<>(ctx.tokens);
        if (tokens.isEmpty()) return null;
        boolean[] consumed = new boolean[tokens.size()];
        ItemRequest req = new ItemRequest();

        // 1. custom name, read from the raw text so capital letters survive
        Matcher m = NAME_PATTERN.matcher(ctx.raw);
        if (m.find()) {
            String name = m.group(2).trim().replaceAll("^[\"']+|[\"']+$", "").trim();
            if (!name.isEmpty()) {
                req.customName = name;
                List<String> seq = new ArrayList<>();
                seq.add(TextUtil.normalize(m.group(1)));
                seq.addAll(TextUtil.tokens(TextUtil.normalize(name)));
                consumeSequence(tokens, consumed, seq);
            }
        }

        // 2. "max enchants" style phrases
        for (String phrase : FULL_PHRASES) {
            List<String> seq = TextUtil.tokens(TextUtil.normalize(phrase));
            if (consumeSequence(tokens, consumed, seq)) req.fullyEnchanted = true;
        }

        // 3. enchantments with their levels
        List<EnchantIndex.Mention> mentions = EnchantIndex.scan(tokens, consumed);
        for (EnchantIndex.Mention mention : mentions) {
            for (int i = mention.start(); i < mention.end(); i++) consumed[i] = true;
        }

        // 4. which number is the amount?
        boolean[] numeric = new boolean[tokens.size()];
        int quantity = -1;
        boolean stackWord = false;
        boolean halfWord = false;
        int wordQuantity = -1;
        List<Integer> beforeIndexes = new ArrayList<>();
        for (EnchantIndex.Mention mention : mentions) {
            if (mention.levelBeforeIndex() >= 0) beforeIndexes.add(mention.levelBeforeIndex());
        }
        // item first (needs the item to judge "4 protection ..."), amounts afterwards
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (consumed[i]) continue;
            String t = tokens.get(i);
            Integer n = NumberWords.parse(t);
            if (n == null) n = NumberWords.times(t);
            if (n != null) {
                numeric[i] = true;
                continue;
            }
            if (Lexicon.QUANTITY_WORDS.contains(t)) {
                numeric[i] = true;
                continue;
            }
            if (Lexicon.STOP.contains(t)) continue;
            remaining.add(t);
        }
        String itemText = Lexicon.expand(String.join(" ", remaining));
        List<String> expandedTokens = TextUtil.tokens(itemText);

        // 5. kits: "netherite set", "diamond armor", "iron tools"
        String material = null;
        boolean wantsArmor = false;
        boolean wantsTools = false;
        boolean kitWord = false;
        for (String t : expandedTokens) {
            if (MATERIALS.containsKey(t) && material == null) material = MATERIALS.get(t);
            if (KIT_WORDS.contains(t)) kitWord = true;
            if (t.equals("armor") || t.equals("armour") || t.equals("full")) wantsArmor = true;
            if (t.equals("tools") || t.equals("tool")) wantsTools = true;
            if (t.equals("set") || t.equals("gear") || t.equals("kit")) {
                wantsArmor = true;
                wantsTools = true;
            }
        }
        if (material != null && kitWord) {
            if (wantsArmor) addKit(req, material, ARMOR);
            if (wantsTools) addKit(req, material, TOOLS);
        }

        // 6. enchanted book
        boolean bookWord = tokens.contains("book") || tokens.contains("books");
        boolean enchantedBook = TextUtil.containsPhrase(ctx.text, "enchanted book") || TextUtil.containsPhrase(ctx.text, "enchanted books");
        if (req.kit.isEmpty() && (enchantedBook || (bookWord && !mentions.isEmpty()))) {
            req.book = true;
            req.item = Items.ENCHANTED_BOOK;
            req.itemKey = "enchanted book";
            req.confidence = 1.0;
        }

        // 7. normal item
        if (req.kit.isEmpty() && req.item == null) {
            NameIndex.Match<Item> match = ItemIndex.get().find(itemText,
                    EngineSettings.FUZZY_THRESHOLD.asDouble(), EngineSettings.MAX_ALTERNATIVES.asInt());
            if (match == null) return null;
            req.item = match.value();
            req.itemKey = match.key();
            req.confidence = match.confidence();
            req.ambiguous = match.ambiguous();
            req.alternatives = new ArrayList<>(match.alternatives());
        }

        // 8. amount and levels, now that the item is known
        boolean plural = isPlural(tokens, req.itemKey);
        for (int i = 0; i < tokens.size(); i++) {
            if (consumed[i]) continue;
            String t = tokens.get(i);
            Integer n = NumberWords.parse(t);
            if (n == null) n = NumberWords.times(t);
            if (n != null) {
                if (beforeIndexes.contains(i)) continue; // decided below
                if (quantity < 0) quantity = n;
                continue;
            }
            if (t.equals("stack") || t.equals("stacks")) stackWord = true;
            if (t.equals("half")) halfWord = true;
            if (t.equals("dozen")) wordQuantity = 12;
            if (t.equals("couple")) wordQuantity = 2;
            if (t.equals("few")) wordQuantity = 3;
            if (t.equals("several")) wordQuantity = 5;
            if (t.equals("bunch")) wordQuantity = 16;
            if (t.equals("lots") || t.equals("tons") || t.equals("loads")) wordQuantity = 32;
        }
        for (EnchantIndex.Mention mention : mentions) {
            if (mention.level() >= 0) {
                req.enchants.add(new ItemRequest.EnchantWish(mention.id(), mention.level()));
            } else if (mention.levelBefore() >= 0) {
                boolean asLevel = !plural && mention.levelBefore() <= EnchantIndex.maxLevel(mention.id());
                if (asLevel) {
                    req.enchants.add(new ItemRequest.EnchantWish(mention.id(), mention.levelBefore()));
                } else {
                    if (quantity < 0) quantity = mention.levelBefore();
                    req.enchants.add(new ItemRequest.EnchantWish(mention.id(), EnchantIndex.LEVEL_MAX));
                }
            } else {
                req.enchants.add(new ItemRequest.EnchantWish(mention.id(), EnchantIndex.LEVEL_MAX));
            }
        }
        if (stackWord) {
            int per = halfWord ? 32 : 64;
            quantity = per * Math.max(1, quantity);
        } else if (wordQuantity > 0) {
            quantity = wordQuantity * Math.max(1, quantity > 0 ? quantity : 1);
        }
        req.quantity = Math.max(1, quantity);

        // 9. "enchanted diamond sword" with no specific enchantment means a fully enchanted one
        boolean saidEnchanted = tokens.contains("enchanted") || tokens.contains("enchant") || tokens.contains("enchants");
        if (saidEnchanted && req.enchants.isEmpty() && !req.fullyEnchanted && !req.book
                && !req.itemKey.startsWith("enchanted")) {
            req.fullyEnchanted = true;
        }
        return req;
    }

    private static boolean isPlural(List<String> tokens, String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) return false;
        String[] words = itemKey.split(" ");
        String last = words[words.length - 1];
        if (last.endsWith("s")) return false;
        return tokens.contains(last + "s") || tokens.contains(last + "es");
    }

    private static void addKit(ItemRequest req, String material, String[] pieces) {
        for (String piece : pieces) {
            Item item = ItemIndex.byPath(material + "_" + piece);
            if (item != null) req.kit.add(item);
        }
        if (!req.kit.isEmpty()) {
            req.itemKey = material + " kit";
        }
    }

    /** Marks the first occurrence of the word sequence as consumed. Returns true when found. */
    private static boolean consumeSequence(List<String> tokens, boolean[] consumed, List<String> seq) {
        if (seq.isEmpty()) return false;
        for (int i = 0; i + seq.size() <= tokens.size(); i++) {
            boolean ok = true;
            for (int k = 0; k < seq.size(); k++) {
                if (consumed[i + k] || !tokens.get(i + k).equals(seq.get(k))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (int k = 0; k < seq.size(); k++) consumed[i + k] = true;
                return true;
            }
        }
        return false;
    }
}
