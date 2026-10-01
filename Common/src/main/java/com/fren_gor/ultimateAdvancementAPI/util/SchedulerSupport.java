package com.fren_gor.ultimateAdvancementAPI.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Selects the scheduler which owns a task while retaining compatibility with Spigot. */
public final class SchedulerSupport {
    public interface Task { void cancel(); }
    private static final boolean FOLIA = exists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final Method TICK_THREAD = tickThreadMethod();
    private SchedulerSupport() {}

    public static boolean isFolia() { return FOLIA; }

    public static boolean isTickThread() {
        if (!FOLIA) return Bukkit.isPrimaryThread();
        try { return (boolean) TICK_THREAD.invoke(null); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot identify Folia tick thread", error); }
    }

    public static boolean owns(Player player) {
        if (!FOLIA) return Bukkit.isPrimaryThread();
        try { return (boolean) FoliaMethods.OWNS.invoke(null, player); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot identify player owner", error); }
    }

    public static Task global(Plugin plugin, long delay, Runnable operation) {
        if (!FOLIA) {
            var task = Bukkit.getScheduler().runTaskLater(plugin, operation, Math.max(1, delay));
            return task::cancel;
        }
        try {
            Object scheduler = FoliaMethods.GLOBAL.invoke(null);
            Object task = FoliaMethods.GLOBAL_DELAYED.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> operation.run(), Math.max(1, delay));
            return () -> { try { FoliaMethods.CANCEL.invoke(task); } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); } };
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot schedule global task", error); }
    }

    public static void player(Plugin plugin, Player player, long delay, Runnable operation) {
        player(plugin, player, delay, operation, () -> {});
    }

    public static void player(Plugin plugin, Player player, long delay, Runnable operation, Runnable retired) {
        if (!plugin.isEnabled()) { retired.run(); return; }
        if (delay == 0 && owns(player)) {
            if (player.isOnline()) operation.run(); else retired.run();
            return;
        }
        Runnable checked = () -> { if (player.isOnline()) operation.run(); else retired.run(); };
        if (!FOLIA) {
            global(plugin, delay, checked);
            return;
        }
        try {
            Object scheduler = FoliaMethods.ENTITY.invoke(player);
            Object task = FoliaMethods.ENTITY_DELAYED.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> checked.run(), retired, Math.max(1, delay));
            if (task == null) retired.run();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot schedule player task", error); }
    }

    public static void async(Plugin plugin, Runnable operation) {
        if (!FOLIA) { Bukkit.getScheduler().runTaskAsynchronously(plugin, operation); return; }
        try {
            Object scheduler = FoliaMethods.ASYNC.invoke(null);
            FoliaMethods.ASYNC_NOW.invoke(scheduler, plugin, (Consumer<Object>) ignored -> operation.run());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot schedule async task", error); }
    }

    private static Method tickThreadMethod() {
        if (!FOLIA) return null;
        for (String name : new String[]{"ca.spottedleaf.moonrise.common.util.TickThread", "io.papermc.paper.util.TickThread"}) {
            try { return Class.forName(name).getMethod("isTickThread"); } catch (ReflectiveOperationException ignored) {}
        }
        throw new IllegalStateException("Unsupported Folia tick thread implementation");
    }
    private static boolean exists(String name) {
        try { Class.forName(name); return true; } catch (ClassNotFoundException ignored) { return false; }
    }

    /** Loaded once on Folia; Spigot does not resolve Paper-only method signatures. */
    private static final class FoliaMethods {
        private static final Method OWNS, GLOBAL, GLOBAL_DELAYED, ENTITY, ENTITY_DELAYED, ASYNC, ASYNC_NOW, CANCEL;
        static {
            try {
                String scheduler = "io.papermc.paper.threadedregions.scheduler.";
                OWNS = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
                GLOBAL = Bukkit.class.getMethod("getGlobalRegionScheduler");
                GLOBAL_DELAYED = Class.forName(scheduler + "GlobalRegionScheduler")
                        .getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                ENTITY = Entity.class.getMethod("getScheduler");
                ENTITY_DELAYED = Class.forName(scheduler + "EntityScheduler")
                        .getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                ASYNC = Bukkit.class.getMethod("getAsyncScheduler");
                ASYNC_NOW = Class.forName(scheduler + "AsyncScheduler").getMethod("runNow", Plugin.class, Consumer.class);
                CANCEL = Class.forName(scheduler + "ScheduledTask").getMethod("cancel");
            } catch (ReflectiveOperationException error) {
                throw new ExceptionInInitializerError(error);
            }
        }
    }
}
