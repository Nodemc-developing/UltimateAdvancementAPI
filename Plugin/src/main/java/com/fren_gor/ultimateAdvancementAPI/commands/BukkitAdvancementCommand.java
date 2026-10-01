package com.fren_gor.ultimateAdvancementAPI.commands;

import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.util.SchedulerSupport;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_ALL;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_ONE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_TAB;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_MAIN_COMMAND;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION_GET;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION_SET;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_ALL;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_ONE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_TAB;

/**
 * Native Bukkit implementation of the {@code /ultimateadvancementapi} command tree (with the usual
 * {@code uaapi} alias). Used as a fallback when the CommandAPI library cannot be downloaded at runtime
 * (offline servers); it delegates advancement operations to {@link CommandsCommon}.
 * Adapted from IOVEYOUMC0/UltimateAdvancementAPI (14b8895), under LGPL-3.0-or-later.
 * On Folia, selectors are limited to @a and @s to avoid cross-region world queries.
 */
public final class BukkitAdvancementCommand extends Command {

    private static final String[] ALIASES = {"uaapi", "uladv", "uladvapi"};
    private static final List<String> COMMAND_LABELS;
    static {
        List<String> labels = new ArrayList<>();
        labels.add("ultimateadvancementapi");
        Collections.addAll(labels, ALIASES);
        for (String name : List.copyOf(labels)) labels.add("ultimateadvancementapi:" + name);
        COMMAND_LABELS = List.copyOf(labels);
    }
    private static volatile BukkitAdvancementCommand INSTANCE;
    private CommandMap registeredMap;
    private final java.util.Map<String, Command> replacedCommands = new java.util.HashMap<>();

    private final AdvancementMain main;
    private final CommandsCommon<CommandException> commandsCommon;

    private BukkitAdvancementCommand(@NotNull AdvancementMain main) {
        super("ultimateadvancementapi", "Command to handle advancements.",
                "Usage: /uaapi <progression|grant|revoke> ...", List.of(ALIASES));
        this.main = Objects.requireNonNull(main, "AdvancementMain is null.");
        this.commandsCommon = new CommandsCommon<>(main, CommandException::new);
        setPermission(PERMISSION_MAIN_COMMAND);
    }

    /**
     * Registers the fallback command on the server's command map. Safe to call more than once (a second call
     * replaces the first). Returns silently if the command map can't be reached via the CraftBukkit API.
     */
    public static void register(@NotNull AdvancementMain main) {
        Objects.requireNonNull(main, "AdvancementMain is null.");
        unregister();
        CommandMap map = craftCommandMap();
        if (map == null) {
            return;
        }
        INSTANCE = new BukkitAdvancementCommand(main);
        INSTANCE.registeredMap = map;
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Command> known = (java.util.Map<String, Command>) map.getClass()
                    .getMethod("getKnownCommands").invoke(map);
            for (String label : COMMAND_LABELS) {
                Command previous = known.get(label);
                if (previous != null) INSTANCE.replacedCommands.put(label, previous);
            }
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().log(Level.WARNING, "[UltimateAdvancementAPI] Couldn't preserve existing command aliases.", e);
        }
        map.register("ultimateadvancementapi", INSTANCE);
    }

    /** Unregisters the fallback command, if previously registered. Idempotent. */
    public static void unregister() {
        BukkitAdvancementCommand instance = INSTANCE;
        INSTANCE = null;
        CommandMap map = instance == null ? null : instance.registeredMap;
        if (instance != null && map != null) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Command> known = (java.util.Map<String, Command>) map.getClass()
                        .getMethod("getKnownCommands").invoke(map);
                // Query owned labels directly: Paper forwards lookups to its live command dispatcher.
                for (String label : COMMAND_LABELS) {
                    if (known.get(label) != instance) continue;
                    Command previous = instance.replacedCommands.get(label);
                    if (previous == null) known.remove(label);
                    else known.put(label, previous);
                }
            } catch (ReflectiveOperationException e) {
                Bukkit.getLogger().log(Level.WARNING, "[UltimateAdvancementAPI] Couldn't remove fallback command aliases.", e);
            }
            instance.unregister(map);
        }
    }

    @Nullable
    private static CommandMap craftCommandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[UltimateAdvancementAPI] Couldn't reach the command map for the fallback /uaapi command.", e);
            return null;
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION_MAIN_COMMAND)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <progression|grant|revoke> ...");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "grant" -> grant(sender, args, label);
            case "revoke" -> revoke(sender, args, label);
            case "progression" -> progression(sender, args, label);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <progression|grant|revoke> ...");
        }
        return true;
    }

    private void grant(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_GRANT)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant <all|tab|one> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "all" -> {
                if (!check(sender, PERMISSION_GRANT_ALL)) {
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 2);
                boolean giveRewards = args.length > 3 && Boolean.parseBoolean(args[3]);
                commandsCommon.grantAll(sender, targets, giveRewards);
            }
            case "tab" -> {
                if (!check(sender, PERMISSION_GRANT_TAB)) {
                    return;
                }
                AdvancementTab tab = requireTab(sender, args.length > 2 ? args[2] : null);
                if (tab == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant tab <advancementTab> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                boolean giveRewards = args.length > 4 && Boolean.parseBoolean(args[4]);
                commandsCommon.grantTab(sender, tab, targets, giveRewards);
            }
            case "one" -> {
                if (!check(sender, PERMISSION_GRANT_ONE)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant one <advancement> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                boolean giveRewards = args.length > 4 && Boolean.parseBoolean(args[4]);
                commandsCommon.grantOne(sender, adv, targets, giveRewards);
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant <all|tab|one> ...");
        }
    }

    private void revoke(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_REVOKE)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke <all|tab|one> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "all" -> {
                if (!check(sender, PERMISSION_REVOKE_ALL)) {
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 2);
                boolean hideTabs = args.length > 3 && Boolean.parseBoolean(args[3]);
                commandsCommon.revokeAll(sender, targets, hideTabs);
            }
            case "tab" -> {
                if (!check(sender, PERMISSION_REVOKE_TAB)) {
                    return;
                }
                AdvancementTab tab = requireTab(sender, args.length > 2 ? args[2] : null);
                if (tab == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke tab <advancementTab> [player] [hideTab]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                boolean hideTab = args.length > 4 && Boolean.parseBoolean(args[4]);
                commandsCommon.revokeTab(sender, tab, targets, hideTab);
            }
            case "one" -> {
                if (!check(sender, PERMISSION_REVOKE_ONE)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke one <advancement> [player]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                commandsCommon.revokeOne(sender, adv, targets);
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke <all|tab|one> ...");
        }
    }

    private void progression(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_PROGRESSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression <get|set> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "get" -> {
                if (!check(sender, PERMISSION_PROGRESSION_GET)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression get <advancement> [player]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                for (Player p : targets) {
                    runSafely(sender, () -> commandsCommon.getProgression(sender, adv, p),
                            "Could not get progression of advancement " + adv);
                }
            }
            case "set" -> {
                if (!check(sender, PERMISSION_PROGRESSION_SET)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                int progression;
                try {
                    progression = Integer.parseInt(args.length > 3 ? args[3] : "-1");
                } catch (NumberFormatException e) {
                    progression = -1;
                }
                if (adv == null || progression < 0) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression set <advancement> <progression> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 4);
                boolean giveRewards = args.length > 5 && Boolean.parseBoolean(args[5]);
                final int prog = progression;
                for (Player p : targets) {
                    runSafely(sender, () -> commandsCommon.setProgression(sender, adv, prog, p, giveRewards),
                            "Could not set progression of advancement " + adv);
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression <get|set> ...");
        }
    }

    private boolean runSafely(CommandSender sender, Runnable action, String error) {
        try {
            action.run();
            return true;
        } catch (CommandException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return false;
        } catch (Exception e) {
            main.getLogger().log(Level.SEVERE, error, e);
            sender.sendMessage(ChatColor.RED + error);
            return false;
        }
    }

    private boolean check(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return false;
        }
        return true;
    }

    // Resolves the optional target-player argument: an "@"-selector or a named online player. When no argument
    // is given the sender is used (players only), matching the CommandAPI's executesPlayer default-to-self path.
    private List<Player> resolveTargets(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            String arg = args[index].trim();
            if (arg.isEmpty()) return List.of();
            if (arg.equals("@a")) return List.copyOf(Bukkit.getOnlinePlayers());
            if (arg.equals("@s")) return sender instanceof Player player ? List.of(player) : List.of();
            if (arg.startsWith("@") && SchedulerSupport.isFolia()) {
                throw new CommandException("On Folia, use an online player name, @a or @s.");
            }
            try {
                if (arg.charAt(0) == '@') {
                    return Bukkit.selectEntities(sender, arg).stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> (Player) e)
                            .toList();
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to exact-name lookup
            }
            Player exact = Bukkit.getPlayerExact(arg);
            if (exact != null) {
                return List.of(exact);
            }
            Player byName = Bukkit.getPlayer(arg);
            return byName == null ? List.of() : List.of(byName);
        }
        return sender instanceof Player player ? List.of(player) : List.of();
    }

    @Nullable
    private AdvancementTab requireTab(CommandSender sender, String arg) {
        if (arg == null) {
            return null;
        }
        AdvancementTab tab = main.getAdvancementTab(arg);
        if (tab == null) {
            sender.sendMessage(ChatColor.RED + "Unknown advancement tab: " + arg);
            return null;
        }
        if (!tab.isActive()) {
            sender.sendMessage(ChatColor.RED + "Invalid advancement tab: " + arg);
            return null;
        }
        return tab;
    }

    @Nullable
    private Advancement requireAdvancement(CommandSender sender, String arg) {
        if (arg == null) {
            return null;
        }
        Advancement adv;
        try {
            adv = main.getAdvancement(arg);
        } catch (IllegalArgumentException e) {
            adv = null;
        }
        if (adv == null) {
            sender.sendMessage(ChatColor.RED + "Unknown advancement: " + arg);
            return null;
        }
        if (!adv.isValid()) {
            sender.sendMessage(ChatColor.RED + "Invalid advancement: " + arg);
            return null;
        }
        return adv;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                               @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION_MAIN_COMMAND)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(args[0], "progression", "grant", "revoke");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "grant", "revoke" -> filter(args[1], "all", "tab", "one");
                case "progression" -> filter(args[1], "get", "set");
                default -> Collections.emptyList();
            };
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        boolean advancementSlot = (sub.equals("grant") || sub.equals("revoke"))
                && (kind.equals("tab") || kind.equals("one"));
        boolean progressionAdvancement = sub.equals("progression")
                && (kind.equals("get") || kind.equals("set"));
        if (args.length == 3 && (advancementSlot || progressionAdvancement)) {
            if (kind.equals("tab")) {
                return filter(args[2], main.getAdvancementTabNamespaces().toArray(new String[0]));
            }
            return filter(args[2], main.filterNamespaces(null).toArray(new String[0]));
        }
        return Collections.emptyList();
    }

    private List<String> filter(String prefix, String... options) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
