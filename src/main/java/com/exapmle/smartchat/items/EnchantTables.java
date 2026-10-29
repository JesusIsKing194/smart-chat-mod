package com.example.smartchat.items;

import com.example.smartchat.engine.EnchantIndex;
import com.example.smartchat.engine.ItemRequest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which enchantments fit which item, the "best" enchantments per item, and applying them. */
public final class EnchantTables {

    private static final Map<String, String[]> BEST = new HashMap<>();
    private static final Map<String, Set<String>> VALID = new HashMap<>();

    private static final String[] ARMOR_COMMON = {"protection", "fire_protection", "blast_protection",
            "projectile_protection", "thorns", "unbreaking", "mending", "vanishing_curse", "binding_curse"};

    static {
        best("sword", "sharpness:5", "looting:3", "fire_aspect:2", "sweeping_edge:3", "knockback:2", "unbreaking:3", "mending:1");
        valid("sword", "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge",
                "unbreaking", "mending", "vanishing_curse");

        best("axe", "sharpness:5", "efficiency:5", "fortune:3", "unbreaking:3", "mending:1");
        valid("axe", "sharpness", "smite", "bane_of_arthropods", "efficiency", "fortune", "silk_touch", "unbreaking",
                "mending", "vanishing_curse");

        for (String tool : new String[]{"pickaxe", "shovel", "hoe"}) {
            best(tool, "efficiency:5", "fortune:3", "unbreaking:3", "mending:1");
            valid(tool, "efficiency", "fortune", "silk_touch", "unbreaking", "mending", "vanishing_curse");
        }

        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            Set<String> set = new HashSet<>(Arrays.asList(ARMOR_COMMON));
            if (piece.equals("helmet")) {
                set.add("respiration");
                set.add("aqua_affinity");
            }
            if (piece.equals("leggings")) set.add("swift_sneak");
            if (piece.equals("boots")) {
                set.addAll(Arrays.asList("feather_falling", "depth_strider", "frost_walker", "soul_speed"));
            }
            VALID.put(piece, set);
        }
        best("helmet", "protection:4", "respiration:3", "aqua_affinity:1", "thorns:3", "unbreaking:3", "mending:1");
        best("chestplate", "protection:4", "thorns:3", "unbreaking:3", "mending:1");
        best("leggings", "protection:4", "swift_sneak:3", "thorns:3", "unbreaking:3", "mending:1");
        best("boots", "protection:4", "feather_falling:4", "depth_strider:3", "soul_speed:3", "thorns:3", "unbreaking:3", "mending:1");

        best("bow", "power:5", "punch:2", "flame:1", "unbreaking:3", "mending:1");
        valid("bow", "power", "punch", "flame", "infinity", "unbreaking", "mending", "vanishing_curse");
        best("crossbow", "quick_charge:3", "piercing:4", "unbreaking:3", "mending:1");
        valid("crossbow", "quick_charge", "multishot", "piercing", "unbreaking", "mending", "vanishing_curse");
        best("trident", "impaling:5", "loyalty:3", "channeling:1", "unbreaking:3", "mending:1");
        valid("trident", "impaling", "riptide", "loyalty", "channeling", "unbreaking", "mending", "vanishing_curse");
        best("mace", "density:5", "wind_burst:3", "fire_aspect:2", "unbreaking:3", "mending:1");
        valid("mace", "density", "breach", "wind_burst", "smite", "bane_of_arthropods", "fire_aspect", "unbreaking",
                "mending", "vanishing_curse");
        best("fishing_rod", "luck_of_the_sea:3", "lure:3", "unbreaking:3", "mending:1");
        valid("fishing_rod", "luck_of_the_sea", "lure", "unbreaking", "mending", "vanishing_curse");
        best("spear", "lunge:3", "unbreaking:3", "mending:1");
        valid("spear", "lunge", "unbreaking", "mending", "vanishing_curse");
        best("durable", "unbreaking:3", "mending:1");
        valid("durable", "unbreaking", "mending", "vanishing_curse");
    }

    private EnchantTables() {}

    private static void best(String type, String... specs) {
        BEST.put(type, specs);
    }

    private static void valid(String type, String... ids) {
        VALID.put(type, new HashSet<>(Arrays.asList(ids)));
    }

    /** "netherite_pickaxe" -> "pickaxe", "bow" -> "bow", unknown -> "". */
    public static String typeOf(String path) {
        for (String t : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "spear"}) {
            if (path.endsWith("_" + t)) return t;
        }
        switch (path) {
            case "bow":
            case "crossbow":
            case "trident":
            case "mace":
            case "fishing_rod":
                return path;
            case "shield":
            case "elytra":
            case "shears":
            case "flint_and_steel":
            case "carrot_on_a_stick":
            case "warped_fungus_on_a_stick":
            case "brush":
                return "durable";
            default:
                return "";
        }
    }

    /**
     * Applies the requested enchantments (and the "best set" when full is true) to the stack.
     * Books accept every enchantment; other items only accept ones that fit (unless enchant_unsupported is on).
     */
    public static void apply(ServerLevel level, ItemStack stack, List<ItemRequest.EnchantWish> wishes, boolean full, boolean book) {
        HolderLookup.RegistryLookup<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String type = typeOf(path);

        if (full && !book) {
            String[] specs = BEST.get(type);
            if (specs != null) {
                for (String spec : specs) {
                    int colon = spec.indexOf(':');
                    add(registry, stack, spec.substring(0, colon), Integer.parseInt(spec.substring(colon + 1)));
                }
            }
        }

        for (ItemRequest.EnchantWish wish : wishes) {
            Set<String> valid = VALID.get(type);
            boolean fits = book || ItemSettings.ENCHANT_ANY.asBoolean() || (valid != null && valid.contains(wish.id()));
            if (fits) add(registry, stack, wish.id(), wish.level());
        }
    }

    private static void add(HolderLookup.RegistryLookup<Enchantment> registry, ItemStack stack, String id, int requested) {
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
        registry.get(key).ifPresent(holder -> {
            int max = holder.value().getMaxLevel();
            int wanted = requested == EnchantIndex.LEVEL_MAX ? max : Math.max(1, requested);
            int cap = ItemSettings.OVER_MAX_LEVELS.asBoolean() ? 255 : max;
            final int level = Math.min(wanted, cap);
            stack.enchant(holder, level);
        });
    }
}
