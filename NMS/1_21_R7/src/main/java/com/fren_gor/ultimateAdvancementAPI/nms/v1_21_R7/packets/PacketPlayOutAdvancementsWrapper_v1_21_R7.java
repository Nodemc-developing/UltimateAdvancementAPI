package com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.packets;

import com.fren_gor.ultimateAdvancementAPI.nms.util.ListSet;
import com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.google.common.collect.Maps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PacketPlayOutAdvancementsWrapper_v1_21_R7 extends PacketPlayOutAdvancementsWrapper {

    private final ClientboundUpdateAdvancementsPacket packet;

    public PacketPlayOutAdvancementsWrapper_v1_21_R7() {
        this.packet = new ClientboundUpdateAdvancementsPacket(true, Collections.emptyList(), Collections.emptySet(), Collections.emptyMap(), true);
    }

    @SuppressWarnings("unchecked")
    public PacketPlayOutAdvancementsWrapper_v1_21_R7(@NotNull Map<AdvancementWrapper, Integer> toSend) {
        Map<Identifier, AdvancementProgress> map = Maps.newHashMapWithExpectedSize(toSend.size());
        for (Entry<AdvancementWrapper, Integer> e : toSend.entrySet()) {
            AdvancementWrapper adv = e.getKey();
            map.put((Identifier) adv.getKey().toNMS(), Util.getAdvancementProgress((AdvancementHolder) adv.toNMS(), e.getValue()));
        }
        this.packet = new ClientboundUpdateAdvancementsPacket(false, (Collection<AdvancementHolder>) ListSet.fromWrapperSet(toSend.keySet()), Collections.emptySet(), map, true);
    }

    @SuppressWarnings("unchecked")
    public PacketPlayOutAdvancementsWrapper_v1_21_R7(@NotNull Set<MinecraftKeyWrapper> toRemove) {
        this.packet = new ClientboundUpdateAdvancementsPacket(false, Collections.emptyList(), (Set<Identifier>) ListSet.fromWrapperSet(toRemove), Collections.emptyMap(), true);
    }

    public PacketPlayOutAdvancementsWrapper_v1_21_R7(Map<AdvancementWrapper, Integer> added, Set<MinecraftKeyWrapper> removed, Map<AdvancementWrapper, Integer> progress) {
        Map<Identifier, AdvancementProgress> changes = Maps.newHashMapWithExpectedSize(progress.size() + added.size());
        Map<AdvancementWrapper, Integer> all = new java.util.HashMap<>(progress);
        all.putAll(added);
        for (Entry<AdvancementWrapper, Integer> entry : all.entrySet()) {
            AdvancementWrapper advancement = entry.getKey();
            changes.put((Identifier) advancement.getKey().toNMS(), Util.getAdvancementProgress((AdvancementHolder) advancement.toNMS(), entry.getValue()));
        }
        var definitions = added.keySet().stream().map(adv -> (AdvancementHolder) adv.toNMS()).toList();
        Set<Identifier> removals = removed.stream().map(key -> (Identifier) key.toNMS()).collect(java.util.stream.Collectors.toSet());
        packet = new ClientboundUpdateAdvancementsPacket(false, definitions, removals, changes, true);
    }

    @Override
    public void sendTo(@NotNull Player player) {
        Util.sendTo(player, packet);
    }
}
