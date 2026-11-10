package com.example.smartchat.gui;

import com.example.smartchat.chat.ChatContext;
import com.example.smartchat.chat.ChatRouter;
import com.example.smartchat.config.ConfigManager;
import com.example.smartchat.config.Setting;
import com.example.smartchat.config.Settings;
import com.example.smartchat.data.PlayerDataStore;
import com.example.smartchat.data.PlayerRecord;
import com.example.smartchat.eggs.Egg;
import com.example.smartchat.engine.Intent;
import com.example.smartchat.games.GameManager;
import com.example.smartchat.gui.MenuUtil.Click;
import com.example.smartchat.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The settings interface, opened by saying "open settings" (or "how to use", "what can you do").
 * Pages: How to Use (with the easter eggs scrambled until you find them), Scores, Campfire Stories progress,
 * My Settings, and, for admins, Server Settings where every option can be changed with a click.
 */
public final class MenuHandler implements ChatRouter.Handler {

    private record Cell(ItemStack stack, Click click) {}

    private static final String[][] FEATURES = {
            {"diamond_pickaxe", "Items", "Say what you want:", "\"can i get a neth pick with fast mining plz\"", "\"protection 4 netherite chestplate\"",
                    "\"enchanted book of mending\"", "\"netherite set\" (a whole armor and tool kit)"},
            {"shulker_box", "Get Stacked", "\"can i get stacked please\"", "Three loot shulker boxes land in front of you.", "Not polite? TNT and anvils rain down!"},
            {"ender_pearl", "Teleports", "\"rtp\" or \"teleport me\"", "\"tp me to steve\"", "\"take me to a desert\" / \"the nearest village\"",
                    "\"random village\" for a random one", "\"go to the nether roof please\" / \"down from the roof\""},
            {"obsidian", "Nether Portal", "\"can i please have a nether portal\"", "It builds around you and lights with lightning."},
            {"iron_bars", "Traps", "\"trap steve please\"", "He gets a 10 second warning. Not polite? It closes on you!"},
            {"water_bucket", "Weather and Time", "\"make it rain\", \"clear the weather\"", "\"make it night\", \"make it noon\""},
            {"feather", "Powers", "\"can i fly\", \"stop flying\"", "\"speed boost\", \"heal me\", \"god mode\"", "\"xray please\", \"summon 3 zombies\""},
            {"jukebox", "Games", "\"rock paper scissors\", \"riddle\", \"trivia\"", "\"20 questions\", \"hot and cold\"", "\"word scramble\", \"tic tac toe\"", "Say \"quit\" to stop a game."},
            {"book", "Assistant", "\"tell me a joke\", \"tell me a story\"", "\"magic 8 ball will i find diamonds\"", "\"my fortune\", \"are you alive\"", "Say \"hey smarty\" and chat!"},
            {"campfire", "Campfire Stories", "Scary choose-your-own-ending stories.", "Say \"campfire story\" and pick A, B or C."},
            {"comparator", "Settings", "Say \"open settings\" any time."}
    };

    private static final String[] CATEGORY_ICONS = {"honey_bottle", "firework_star", "feather", "cake", "book"};

    /** Touching this method makes sure the class is loaded. */
    public static void init() {}

    // ------------------------------------------------------------------ chat

    @Override
    public boolean handle(ChatContext ctx) {
        if (ctx.tokens.size() > 6) return false;
        boolean menu = ctx.intents().primary() == Intent.MENU
                || ctx.hasAny("open settings", "settings menu", "smart chat menu", "smartchat menu", "open menu",
                "how to use", "what can you do", "help menu", "open help");
        if (!menu) return false;
        if (ctx.hasAny("how to use", "what can you do", "help menu", "open help")) openHowTo(ctx.player);
        else openMain(ctx.player);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isAdmin(ServerPlayer p) {
        return Settings.ADMIN_NAMES.asList().contains(p.getName().getString().toLowerCase());
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static ItemStack icon(String item, String name, ChatFormatting color, List<String> lore) {
        return MenuUtil.icon(item, name, color, lore.toArray(new String[0]));
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.replace('_', ' ').replace('-', ' ').split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static void openPaged(ServerPlayer p, String title, List<Cell> cells, int page, IntConsumer reopen, Runnable back) {
        int perPage = 45;
        int pages = Math.max(1, (cells.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        MenuUtil.Menu menu = MenuUtil.menu(title + (pages > 1 ? "  (" + (pg + 1) + "/" + pages + ")" : ""), 6);
        for (int i = 0; i < perPage; i++) {
            int index = pg * perPage + i;
            if (index >= cells.size()) break;
            menu.set(i, cells.get(index).stack(), cells.get(index).click());
        }
        menu.set(45, MenuUtil.icon("arrow", "Back", ChatFormatting.YELLOW), (pl, b) -> back.run());
        if (pg > 0) menu.set(48, MenuUtil.icon("spectral_arrow", "Previous page", ChatFormatting.AQUA), (pl, b) -> reopen.accept(pg - 1));
        if (pg < pages - 1) menu.set(50, MenuUtil.icon("spectral_arrow", "Next page", ChatFormatting.AQUA), (pl, b) -> reopen.accept(pg + 1));
        menu.set(53, MenuUtil.icon("barrier", "Close", ChatFormatting.RED), (pl, b) -> pl.closeContainer());
        menu.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ main page

    public static void openMain(ServerPlayer p) {
        MenuUtil.Menu menu = MenuUtil.menu("Smart Chat", 3);
        menu.set(10, MenuUtil.icon("book", "How to Use", ChatFormatting.GOLD, "Everything the mod can do,", "plus secrets to discover."),
                (pl, b) -> openHowTo(pl));
        menu.set(12, MenuUtil.icon("gold_ingot", "Scores", ChatFormatting.YELLOW, "Game points and leaderboards."),
                (pl, b) -> openScores(pl, 0));
        menu.set(14, MenuUtil.icon("campfire", "Campfire Stories", ChatFormatting.GOLD, "Your endings found so far."),
                (pl, b) -> openStories(pl, 0));
        menu.set(16, MenuUtil.icon("comparator", "My Settings", ChatFormatting.AQUA, "Personal options."),
                (pl, b) -> openMySettings(pl));
        if (isAdmin(p)) {
            menu.set(22, MenuUtil.icon("command_block", "Server Settings", ChatFormatting.RED, "Admins only: change any option."),
                    (pl, b) -> openServerSettings(pl, 0));
        }
        menu.set(26, MenuUtil.icon("barrier", "Close", ChatFormatting.RED), (pl, b) -> pl.closeContainer());
        menu.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ how to use and secrets

    public static void openHowTo(ServerPlayer p) {
        openHowTo(p, 0);
    }

    private static void openHowTo(ServerPlayer p, int page) {
        List<Cell> cells = new ArrayList<>();
        int found = Egg.discoveredCount(p);
        cells.add(new Cell(MenuUtil.icon("ender_eye", "Secrets  (" + found + "/" + Egg.total() + " found)", ChatFormatting.LIGHT_PURPLE,
                "Hidden easter eggs! Try saying things", "in chat. When you find one it", "turns into normal text here."),
                (pl, b) -> openSecrets(pl, 0)));
        for (String[] f : FEATURES) {
            List<String> lore = new ArrayList<>();
            for (int i = 2; i < f.length; i++) lore.add(f[i]);
            cells.add(new Cell(icon(f[0], f[1], ChatFormatting.GREEN, lore), null));
        }
        openPaged(p, "How to Use", cells, page, pg -> openHowTo(p, pg), () -> openMain(p));
    }

    private static void openSecrets(ServerPlayer p, int page) {
        boolean admin = isAdmin(p);
        List<Cell> cells = new ArrayList<>();
        for (Egg egg : Egg.all()) {
            boolean known = Egg.isDiscovered(p, egg.id);
            if (known) {
                List<String> lore = new ArrayList<>(wrap(egg.description, 36));
                lore.add("Found!");
                cells.add(new Cell(icon(CATEGORY_ICONS[egg.category.ordinal()], egg.name, ChatFormatting.GREEN, lore), null));
            } else if (admin) {
                List<String> lore = new ArrayList<>(wrap(egg.description, 36));
                lore.add("Admin view - you haven't found this one yet.");
                lore.add("Setting: eggs.egg_" + String.format("%02d", egg.number));
                cells.add(new Cell(icon(CATEGORY_ICONS[egg.category.ordinal()], egg.name, ChatFormatting.GOLD, lore), null));
            } else {
                cells.add(new Cell(MenuUtil.hiddenIcon("ender_eye", egg.name, "Keep exploring to discover this secret."), null));
            }
        }
        openPaged(p, "Secrets " + Egg.discoveredCount(p) + "/" + Egg.total(), cells, page, pg -> openSecrets(p, pg), () -> openHowTo(p));
    }

    // ------------------------------------------------------------------ scores and stories

    private static void openScores(ServerPlayer p, int page) {
        List<Cell> cells = new ArrayList<>();
        List<String> overall = new ArrayList<>();
        int rank = 1;
        for (GameManager.Entry e : GameManager.leaderboard(null, GameManager.LEADERBOARD_SIZE.asInt())) {
            overall.add(rank++ + ". " + e.name() + "  -  " + e.points());
        }
        if (overall.isEmpty()) overall.add("Nobody has scored yet. Be the first!");
        cells.add(new Cell(icon("nether_star", "All Games - Top Players", ChatFormatting.GOLD, overall), null));

        for (GameManager.Game game : GameManager.all()) {
            List<String> lore = new ArrayList<>();
            lore.add("Your points: " + GameManager.points(p, game.id()) + "   Wins: " + GameManager.wins(p, game.id()));
            lore.add("Top players:");
            int place = 1;
            for (GameManager.Entry e : GameManager.leaderboard(game.id(), 3)) lore.add(place++ + ". " + e.name() + "  -  " + e.points());
            if (place == 1) lore.add("(nobody yet)");
            cells.add(new Cell(icon("gold_ingot", game.name(), ChatFormatting.YELLOW, lore), null));
        }
        openPaged(p, "Scores", cells, page, pg -> openScores(p, pg), () -> openMain(p));
    }

    private static void openStories(ServerPlayer p, int page) {
        PlayerRecord record = PlayerDataStore.get(p);
        List<Cell> cells = new ArrayList<>();
        int total = 0;
        for (Set<String> endings : record.endings.values()) total += endings.size();
        cells.add(new Cell(icon("campfire", "Your Campfire Stories", ChatFormatting.GOLD,
                List.of("Endings found: " + total, "Say \"campfire story\" to hear a new one!")), null));
        for (Map.Entry<String, Set<String>> e : record.endings.entrySet()) {
            cells.add(new Cell(icon("written_book", pretty(e.getKey()), ChatFormatting.WHITE,
                    List.of("Endings found: " + e.getValue().size() + " / 27")), null));
        }
        openPaged(p, "Campfire Stories", cells, page, pg -> openStories(p, pg), () -> openMain(p));
    }

    // ------------------------------------------------------------------ personal settings

    private static void openMySettings(ServerPlayer p) {
        PlayerRecord record = PlayerDataStore.get(p);
        MenuUtil.Menu menu = MenuUtil.menu("My Settings", 3);
        menu.set(11, MenuUtil.icon(record.trapOptOut ? "lime_dye" : "gray_dye",
                "Traps: " + (record.trapOptOut ? "OFF (nobody can trap you)" : "ON"), ChatFormatting.AQUA,
                "Click to switch. When off, other players", "cannot trap you."), (pl, b) -> {
            record.trapOptOut = !record.trapOptOut;
            PlayerDataStore.markDirty();
            openMySettings(pl);
        });
        menu.set(15, MenuUtil.icon(record.quietAi ? "lime_dye" : "gray_dye",
                "Quiet assistant: " + (record.quietAi ? "ON" : "OFF"), ChatFormatting.AQUA,
                "Click to switch. When on, the assistant", "stops chatting with you (everything", "else still works)."), (pl, b) -> {
            record.quietAi = !record.quietAi;
            PlayerDataStore.markDirty();
            openMySettings(pl);
        });
        menu.set(22, MenuUtil.icon("arrow", "Back", ChatFormatting.YELLOW), (pl, b) -> openMain(pl));
        menu.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ server settings (admins)

    private static void openServerSettings(ServerPlayer p, int page) {
        if (!isAdmin(p)) {
            Messages.say(p, "Only admins can change server settings.");
            return;
        }
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(MenuUtil.icon("clock", "Reload settings file", ChatFormatting.AQUA,
                "Re-reads settings.cfg after you edit it", "by hand. (It also reloads by itself.)"), (pl, b) -> {
            ConfigManager.reload();
            Messages.say(pl, "Settings reloaded.");
            openServerSettings(pl, page);
        }));
        for (String section : Settings.sections()) {
            int count = Settings.inSection(section).size();
            cells.add(new Cell(MenuUtil.icon("writable_book", Settings.sectionTitle(section), ChatFormatting.GOLD,
                    count + " settings", "Click to open."), (pl, b) -> openSection(pl, section, 0)));
        }
        openPaged(p, "Server Settings", cells, page, pg -> openServerSettings(p, pg), () -> openMain(p));
    }

    private static void openSection(ServerPlayer p, String section, int page) {
        if (!isAdmin(p)) return;
        List<Cell> cells = new ArrayList<>();
        for (Setting s : Settings.inSection(section)) {
            List<String> lore = new ArrayList<>(wrap(s.description, 36));
            Click click = null;
            String item;
            String name = s.key + ":  " + shorten(s.raw());
            switch (s.type) {
                case BOOLEAN:
                    item = s.asBoolean() ? "lime_dye" : "gray_dye";
                    lore.add("Click to turn " + (s.asBoolean() ? "OFF" : "ON") + ".");
                    click = (pl, b) -> {
                        s.set(s.asBoolean() ? "false" : "true");
                        ConfigManager.save();
                        openSection(pl, section, page);
                    };
                    break;
                case INTEGER:
                case DECIMAL:
                    item = "repeater";
                    lore.add("Left click: more   Right click: less");
                    lore.add("Allowed: " + s.min + " to " + s.max);
                    click = (pl, b) -> {
                        adjust(s, b == 0);
                        ConfigManager.save();
                        openSection(pl, section, page);
                    };
                    break;
                default:
                    item = "paper";
                    lore.add("Edit this one in settings.cfg.");
                    break;
            }
            cells.add(new Cell(icon(item, name, ChatFormatting.WHITE, lore), click));
        }
        openPaged(p, Settings.sectionTitle(section), cells, page, pg -> openSection(p, section, pg), () -> openServerSettings(p, 0));
    }

    private static String shorten(String value) {
        return value.length() <= 24 ? value : value.substring(0, 22) + "...";
    }

    private static void adjust(Setting s, boolean up) {
        double step = Math.max(s.type == Setting.Type.INTEGER ? 1 : 0.05, (s.max - s.min) / 20.0);
        if (s.type == Setting.Type.INTEGER) {
            int stepInt = (int) Math.max(1, Math.round(step));
            s.set(Integer.toString(s.asInt() + (up ? stepInt : -stepInt)));
        } else {
            s.set(Double.toString(Math.round((s.asDouble() + (up ? step : -step)) * 100.0) / 100.0));
        }
    }
}
