package com.fren_gor.ultimateAdvancementAPI.nms.v26_3_R1;

import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.VanillaAdvancementDisablerWrapper;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import java.lang.reflect.Field;
import java.util.*;

public final class VanillaAdvancementDisablerWrapper_v26_3_R1 extends VanillaAdvancementDisablerWrapper {
    public static void disableVanillaAdvancements(boolean vanilla, boolean recipes) throws Exception {
        var manager = ((CraftServer) Bukkit.getServer()).getServer().getAdvancements();
        Set<Identifier> selected = new HashSet<>(), removed = new HashSet<>();
        for (Identifier key : manager.advancements.keySet()) {
            boolean recipe = key.getPath().startsWith("recipes/");
            if (key.getNamespace().equals("minecraft") && (recipe ? recipes : vanilla)) selected.add(key);
        }
        // The 26.3 tree has no listener; collect descendant removals before changing it.
        for (AdvancementNode node : manager.tree().nodes()) {
            for (AdvancementNode ancestor = node; ancestor != null; ancestor = ancestor.parent()) {
                if (selected.contains(ancestor.holder().id())) { removed.add(node.holder().id()); break; }
            }
        }
        Map<Identifier, net.minecraft.advancements.AdvancementHolder> retained = new HashMap<>(manager.advancements);
        removed.forEach(retained::remove);
        manager.advancements = Map.copyOf(retained);
        manager.tree().remove(selected);
        var packet = new ClientboundUpdateAdvancementsPacket(false, List.of(), removed, Map.of(), false);
        Field firstPacket = PlayerAdvancements.class.getDeclaredField("isFirstPacket");
        firstPacket.setAccessible(true);
        for (var player : Bukkit.getOnlinePlayers()) {
            runForPlayer(player, () -> {
                var handle = ((CraftPlayer) player).getHandle();
                var advancements = handle.getAdvancements();
                advancements.reload(manager);
                try { firstPacket.setBoolean(advancements, false); }
                catch (IllegalAccessException error) { throw new IllegalStateException(error); }
                handle.connection.send(packet);
            });
        }
    }
    private VanillaAdvancementDisablerWrapper_v26_3_R1() {}
}
