package com.fren_gor.ultimateAdvancementAPI.commands;

import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.util.SchedulerSupport;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Common code for command implementations.
 *
 * @hidden
 */
@Internal
public final class CommandsCommon<Error extends Exception> {

    private final AdvancementMain main;
    private final CommandAPIMethods<Error> commandAPI;

    public CommandsCommon(@NotNull AdvancementMain main, @NotNull CommandAPIMethods<Error> commandAPI) {
        this.main = Objects.requireNonNull(main, "AdvancementMain is null.");
        this.commandAPI = Objects.requireNonNull(commandAPI, "CommandAPIMethods is null.");
    }

    public void grantAll(CommandSender sender, Player player, boolean giveRewards) throws Error {
        grantAll(sender, List.of(player), giveRewards);
    }

    public void grantAll(CommandSender sender, Collection<Player> players, boolean giveRewards) throws Error {
        validatePlayerArgument(players);
        players = ownedPlayers(sender, players, p -> grantAll(sender, List.of(p), giveRewards));
        if (players.isEmpty()) return;
        boolean failed = false;
        for (AdvancementTab m : main.getTabs()) {
            if (m.isActive()) {
                for (Advancement a : m.getAdvancements()) {
                    for (Player p : players) {
                        failed |= runSafely(sender, () -> a.grant(p, giveRewards), () -> "Could not grant advancement " + a + " to " + p.getName());
                    }
                }
            }
        }
        for (Player p : players) {
            if (failed) {
                reply(sender, ChatColor.RED + "Could not grant every advancement to " + p.getName());
            } else {
                reply(sender, ChatColor.GREEN + "All advancements have been granted to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public void grantTab(CommandSender sender, AdvancementTab tab, Player player, boolean giveRewards) throws Error {
        grantTab(sender, tab, List.of(player), giveRewards);
    }

    public void grantTab(CommandSender sender, AdvancementTab tab, Collection<Player> players, boolean giveRewards) throws Error {
        validatePlayerArgument(players);
        if (!tab.isActive()) {
            throw commandAPI.failWithString("Advancement tab is not active.");
        }
        players = ownedPlayers(sender, players, p -> grantTab(sender, tab, List.of(p), giveRewards));
        if (players.isEmpty()) return;
        boolean failed = false;
        for (Advancement a : tab.getAdvancements()) {
            for (Player p : players) {
                failed |= runSafely(sender, () -> a.grant(p, giveRewards), () -> "Could not grant advancement " + a + " to " + p.getName());
            }
        }
        for (Player p : players) {
            if (failed) {
                reply(sender, ChatColor.RED + "Could not grant every advancement of tab " + tab + " to " + p.getName());
            } else {
                reply(sender, ChatColor.GREEN + "All advancements of tab " + ChatColor.YELLOW + tab + ChatColor.GREEN + " have been granted to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public void grantOne(CommandSender sender, Advancement advancement, Player player, boolean giveRewards) throws Error {
        grantOne(sender, advancement, List.of(player), giveRewards);
    }

    public void grantOne(CommandSender sender, Advancement advancement, Collection<Player> players, boolean giveRewards) throws Error {
        validatePlayerArgument(players);
        players = ownedPlayers(sender, players, p -> grantOne(sender, advancement, List.of(p), giveRewards));
        if (players.isEmpty()) return;
        for (Player p : players) {
            boolean failed = runSafely(sender, () -> {
                advancement.getAdvancementTab().showTab(p);
                advancement.grant(p, giveRewards);
            }, () -> "Could not grant advancement " + advancement + " to " + p.getName());
            if (!failed) {
                reply(sender, ChatColor.GREEN + "Advancement " + ChatColor.YELLOW + advancement.getKey() + ChatColor.GREEN + " has been granted to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public void revokeAll(CommandSender sender, Player player, boolean hideTabs) throws Error {
        revokeAll(sender, List.of(player), hideTabs);
    }

    public void revokeAll(CommandSender sender, Collection<Player> players, boolean hideTabs) throws Error {
        validatePlayerArgument(players);
        players = ownedPlayers(sender, players, p -> revokeAll(sender, List.of(p), hideTabs));
        if (players.isEmpty()) return;
        boolean failed = false;
        for (AdvancementTab m : main.getTabs()) {
            var advancements = m.getAdvancements();
            for (Player p : players) {
                for (Advancement a : advancements) {
                    failed |= runSafely(sender, () -> a.revoke(p), () -> "Could not revoke advancement " + a + " to " + p.getName());
                }
                if (hideTabs) {
                    runSafely(sender, () -> m.hideTab(p), () -> "Could not hide advancement tab " + m + " to " + p.getName());
                }
            }
        }
        for (Player p : players) {
            if (failed) {
                reply(sender, ChatColor.RED + "Could not revoke every advancement to " + p.getName());
            } else {
                reply(sender, ChatColor.GREEN + "All advancements have been revoked to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public void revokeTab(CommandSender sender, AdvancementTab tab, Player player, boolean hideTab) throws Error {
        revokeTab(sender, tab, List.of(player), hideTab);
    }

    public void revokeTab(CommandSender sender, AdvancementTab tab, Collection<Player> players, boolean hideTab) throws Error {
        validatePlayerArgument(players);
        if (!tab.isActive()) {
            throw commandAPI.failWithString("Advancement tab is not active.");
        }
        players = ownedPlayers(sender, players, p -> revokeTab(sender, tab, List.of(p), hideTab));
        if (players.isEmpty()) return;
        var advancements = tab.getAdvancements();
        for (Player p : players) {
            boolean failed = false;
            for (Advancement a : advancements) {
                failed |= runSafely(sender, () -> a.revoke(p), () -> "Could not revoke advancement " + a + " to " + p.getName());
            }
            if (hideTab) {
                runSafely(sender, () -> tab.hideTab(p), () -> "Could not hide advancement tab " + tab + " to " + p.getName());
            }
            if (failed) {
                reply(sender, ChatColor.RED + "Could not revoke every advancement of tab " + tab + " to " + p.getName());
            } else {
                reply(sender, ChatColor.GREEN + "All advancements of tab " + ChatColor.YELLOW + tab + ChatColor.GREEN + " have been revoked to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public void revokeOne(CommandSender sender, Advancement advancement, Player player) throws Error {
        revokeOne(sender, advancement, List.of(player));
    }

    public void revokeOne(CommandSender sender, Advancement advancement, Collection<Player> players) throws Error {
        validatePlayerArgument(players);
        players = ownedPlayers(sender, players, p -> revokeOne(sender, advancement, List.of(p)));
        if (players.isEmpty()) return;
        for (Player p : players) {
            boolean failed = runSafely(sender, () -> {
                advancement.revoke(p);
            }, () -> "Could not revoke advancement " + advancement + " to " + p.getName());
            if (!failed) {
                reply(sender, ChatColor.GREEN + "Advancement " + ChatColor.YELLOW + advancement + ChatColor.GREEN + " has been revoked to " + ChatColor.YELLOW + p.getName());
            }
        }
    }

    public int getProgression(CommandSender sender, Advancement advancement, Player player) throws Error {
        return getProgression(sender, advancement, List.of(player));
    }

    public int getProgression(CommandSender sender, Advancement advancement, Collection<Player> players) throws Error {
        validatePlayerArgument(players);
        int[] progression = {0}; // Avoid lambda error
        for (Player p : players) {
            boolean failed = runSafely(sender, () -> {
                progression[0] = advancement.getProgression(p);
            }, () -> {
                return "Could not get " + p.getName() + "'s progression of advancement " + advancement;
            });
            if (!failed) {
                reply(sender, ChatColor.YELLOW + p.getName() + ChatColor.GREEN + " progression is " + ChatColor.YELLOW + progression[0] + '/' + advancement.getMaxProgression());
            }
        }
        return progression[0];
    }

    public void setProgression(CommandSender sender, Advancement advancement, int progression, Player player, boolean giveRewards) throws Error {
        setProgression(sender, advancement, progression, List.of(player), giveRewards);
    }

    public void setProgression(CommandSender sender, Advancement advancement, int progression, Collection<Player> players, boolean giveRewards) throws Error {
        validatePlayerArgument(players);
        players = ownedPlayers(sender, players, p -> setProgression(sender, advancement, progression, List.of(p), giveRewards));
        if (players.isEmpty()) return;
        final int progr = Math.min(advancement.getMaxProgression(), progression);
        for (Player p : players) {
            boolean failed = runSafely(sender, () -> {
                advancement.setProgression(p, progr, giveRewards);
            }, () -> {
                return "Could not set " + p.getName() + "'s progression of advancement " + advancement + " to " + progr + '/' + advancement.getMaxProgression();
            });
            if (!failed) {
                reply(sender, ChatColor.GREEN + "Progression of " + ChatColor.YELLOW + p.getName() + ChatColor.GREEN + " has been set to " + ChatColor.YELLOW + progr + '/' + advancement.getMaxProgression());
            }
        }
    }

    private boolean runSafely(CommandSender sender, Runnable action, Supplier<String> errorGenerator) {
        try {
            action.run();
        } catch (UnsupportedOperationException ignored) {
        } catch (Exception e) {
            String error = errorGenerator.get();
            main.getLogger().log(Level.SEVERE, error, e);
            reply(sender, ChatColor.RED + error);
            return true;
        }
        return false;
    }

    private void validatePlayerArgument(@NotNull Collection<Player> players) throws Error {
        if (players.isEmpty()) {
            throw commandAPI.failWithString("No player has been provided.");
        }
    }

    private Collection<Player> ownedPlayers(CommandSender sender, Collection<Player> players, PlayerAction action) {
        List<Player> owned = new ArrayList<>(players.size());
        for (Player player : players) {
            if (SchedulerSupport.owns(player)) owned.add(player);
            else SchedulerSupport.player(main.getOwningPlugin(), player, 0, () -> {
                try { action.run(player); }
                catch (Exception error) {
                    main.getLogger().log(Level.WARNING, "Advancement command failed for " + player.getName(), error);
                    reply(sender, ChatColor.RED + error.getMessage());
                }
            });
        }
        return owned;
    }

    private void reply(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            SchedulerSupport.player(main.getOwningPlugin(), player, 0, () -> player.sendMessage(message));
        } else sender.sendMessage(message);
    }

    @FunctionalInterface
    private interface PlayerAction { void run(Player player) throws Exception; }

    /**
     * @hidden
     */
    @Internal
    @FunctionalInterface
    public interface CommandAPIMethods<Error extends Exception> {
        Error failWithString(String string);
    }
}
