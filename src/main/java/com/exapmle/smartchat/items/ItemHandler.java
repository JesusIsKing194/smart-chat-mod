package com.example.smartchat.items;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.Settings;
import com.example.smartchat.engine.EngineSettings;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.engine.ItemIndex;
import com.example.smartchat.engine.ItemRequest;
import com.example.smartchat.util.Messages;
import com.example.smartchat.util.NumberWords;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** "can i get a neth pick with fast mining plz" -> a fully understood, delivered item. */
public final class ItemHandler implements ChatRouter.Handler {

    private record Pending(ItemRequest request, List<String> keys, long expires) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    @Override
    public boolean handle(ChatContext ctx) {
        if (!ItemSettings.ENABLED.asBoolean()) return false;
        UUID id = ctx.player.getUUID();
        ItemRequest req = null;

        // an answer to "did you mean...?"
        Pending pending = PENDING.get(id);
        if (pending != null) {
            if (System.currentTimeMillis() > pending.expires()) {
                PENDING.remove(id);
            } else {
                String chosen = choose(ctx, pending.keys());
                if (chosen != null) {
                    PENDING.remove(id);
                    req = pending.request();
                    req.item = ItemIndex.get().get(chosen);
                    req.itemKey = chosen;
                    req.ambiguous = false;
                    req.alternatives = new ArrayList<>();
                    if (req.item == null) req = null;
                }
            }
        }

        if (req == null) {
            if (ctx.intents().primary() != Intent.ITEM) return false;
            req = ctx.itemRequest();
            if (req == null) return hint(ctx);
            if (req.ambiguous && !req.isKit() && EngineSettings.ASK_WHEN_UNSURE.asBoolean()) return ask(ctx, req);
        }
        return fulfil(ctx, req);
    }

    private static String choose(ChatContext ctx, List<String> keys) {
        if (ctx.tokens.size() == 1) {
            String t = ctx.tokens.get(0);
            Integer n = NumberWords.parse(t);
            if (n == null) {
                switch (t) {
                    case "first": n = 1; break;
                    case "second": n = 2; break;
                    case "third": n = 3; break;
                    case "fourth": n = 4; break;
                    case "fifth": n = 5; break;
                    default: break;
                }
            }
            if (n != null && n >= 1 && n <= keys.size()) return keys.get(n - 1);
        }
        String best = null;
        for (String key : keys) {
            if (ctx.has(key) && (best == null || key.length() > best.length())) best = key;
        }
        return best;
    }

    private static boolean hint(ChatContext ctx) {
        // being polite alone ("thanks", "plz") is not an item request; it has to actually ask for something
        boolean clearRequest = ctx.hasAny("give me", "gimme", "get me", "i want", "i need", "spawn", "can i have",
                "can i get", "could i have", "could i get", "may i have", "i would like", "id like");
        if (!clearRequest) return false;
        if (Settings.HINTS.asBoolean()) {
            ctx.reply("I couldn't find that item. Try its name, like \"diamond sword\" or \"totem\".");
        }
        return true;
    }

    private static boolean ask(ChatContext ctx, ItemRequest req) {
        List<String> keys = new ArrayList<>();
        keys.add(req.itemKey);
        for (String alt : req.alternatives) {
            if (!keys.contains(alt)) keys.add(alt);
        }
        PENDING.put(ctx.player.getUUID(), new Pending(req, keys, System.currentTimeMillis() + 30_000L));
        StringBuilder sb = new StringBuilder("Did you mean: ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append("   ");
            sb.append(i + 1).append(") ").append(Delivery.pretty(keys.get(i)));
        }
        sb.append("  - reply with the number.");
        ctx.reply(sb.toString());
        return true;
    }

    private static boolean isBlacklisted(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        String trimmed = path.replaceAll("(es|s)$", "");
        for (String entry : ItemSettings.BLACKLIST.asList()) {
            String e = entry.contains(":") ? entry.substring(entry.indexOf(':') + 1) : entry;
            if (e.equals(path) || e.equals(trimmed)) return true;
        }
        return false;
    }

    private static boolean fulfil(ChatContext ctx, ItemRequest req) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level;
        UUID id = player.getUUID();

        if (ItemSettings.REQUIRE_POLITE.asBoolean() && !ctx.polite) {
            ctx.reply("Ask nicely and I'll help! (say please)");
            return true;
        }

        long now = System.currentTimeMillis();
        int cooldown = ItemSettings.COOLDOWN_SECONDS.asInt();
        Long last = LAST_REQUEST.get(id);
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Messages.actionBar(player, "Slow down! Try again in " + left + "s");
            return true;
        }

        List<Item> items = req.isKit() ? req.kit : (req.item == null ? List.<Item>of() : List.of(req.item));
        for (Item item : items) {
            if (isBlacklisted(item)) {
                level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                ctx.reply("That item is blocked on this server.");
                return true;
            }
        }

        List<ItemStack> stacks = ItemBuilder.build(level, req);
        if (stacks.isEmpty()) return false;
        LAST_REQUEST.put(id, now);

        Delivery.Result result = Delivery.deliver(player, stacks);
        int total = 0;
        for (ItemStack s : stacks) total += s.getCount();
        String what = req.isKit() ? Delivery.pretty(req.itemKey) : Delivery.pretty(req.itemKey);
        StringBuilder msg = new StringBuilder("Here you go: ").append(total).append("x ").append(what);
        if (req.customName != null) msg.append(" named \"").append(req.customName).append("\"");
        if (result.toInventory() > 0) msg.append(" (some went straight into your inventory because the drop was blocked)");
        ctx.reply(msg.toString());
        return true;
    }
}
