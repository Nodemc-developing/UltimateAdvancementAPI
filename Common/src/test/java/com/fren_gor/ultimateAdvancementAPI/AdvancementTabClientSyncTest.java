package com.fren_gor.ultimateAdvancementAPI;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.database.*;
import com.fren_gor.ultimateAdvancementAPI.exceptions.UserNotLoadedException;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.*;
import com.fren_gor.ultimateAdvancementAPI.tests.Utils;
import com.fren_gor.ultimateAdvancementAPI.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.*;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdvancementTabClientSyncTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<SchedulerSupport> scheduler;
    private MockedStatic<PacketPlayOutAdvancementsWrapper> packets;
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<Update> sent = new ArrayList<>();
    private final UUID uuid = UUID.randomUUID();
    private AdvancementTab tab;
    private DatabaseManager database;
    private Player player;
    private TeamProgression team;
    private AdvancementWrapper wrapper;
    private int progress;
    private boolean visible = true;
    private record Update(Map<AdvancementWrapper, Integer> added, Set<MinecraftKeyWrapper> removed, Map<AdvancementWrapper, Integer> progress) {}

    @Before public void setup() throws Exception {
        bukkit = Utils.mockServer();
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn("SyncTests");
        database = mock(DatabaseManager.class);
        when(database.getOwningPlugin()).thenReturn(plugin);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        team = mock(TeamProgression.class);
        when(team.isValid()).thenReturn(true);
        when(database.getTeamProgression(uuid)).thenReturn(team);
        when(database.getTeamProgression(player)).thenReturn(team);
        when(database.isLoaded(uuid)).thenReturn(true);
        doAnswer(invocation -> { invocation.<Consumer<UUID>>getArgument(0).accept(uuid); return null; }).when(team).forEachMember(any());
        scheduler = mockStatic(SchedulerSupport.class);
        scheduler.when(() -> SchedulerSupport.global(any(), anyLong(), any())).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(2)); return (SchedulerSupport.Task) () -> {};
        });
        scheduler.when(() -> SchedulerSupport.player(any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(3)); return null;
        });
        scheduler.when(() -> SchedulerSupport.player(any(), any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(3)); return null;
        });
        packets = mockStatic(PacketPlayOutAdvancementsWrapper.class);
        packets.when(() -> PacketPlayOutAdvancementsWrapper.craftUpdatePacket(anyMap(), anySet(), anyMap())).thenAnswer(invocation -> {
            sent.add(new Update(Map.copyOf(invocation.getArgument(0)), Set.copyOf(invocation.getArgument(1)), Map.copyOf(invocation.getArgument(2))));
            return mock(PacketPlayOutAdvancementsWrapper.class);
        });
        tab = new AdvancementTab(plugin, database, "sync");
        Field initialised = AdvancementTab.class.getDeclaredField("initialised"); initialised.setAccessible(true); initialised.set(tab, true);
        Field nodes = AdvancementTab.class.getDeclaredField("advancements"); nodes.setAccessible(true);
        wrapper = mock(AdvancementWrapper.class);
        when(wrapper.getKey()).thenReturn(mock(MinecraftKeyWrapper.class));
        Advancement advancement = mock(Advancement.class);
        doAnswer(invocation -> { if (visible) invocation.<Map<AdvancementWrapper,Integer>>getArgument(1).put(wrapper, progress); return null; })
                .when(advancement).onUpdate(any(), anyMap());
        @SuppressWarnings("unchecked") Map<AdvancementKey, Advancement> registered = (Map<AdvancementKey, Advancement>) nodes.get(tab);
        registered.put(new AdvancementKey("sync", "root"), advancement);
    }
    @After public void close() {
        if (packets != null) packets.close();
        if (scheduler != null) scheduler.close();
        if (bukkit != null) bukkit.close();
    }
    private void drain() {
        int budget = 50;
        while (!tasks.isEmpty()) { assertTrue("Tasks must converge", --budget > 0); tasks.remove().run(); }
    }

    @Test public void firstShowCanRetryAfterDatabaseLoading() {
        when(database.getTeamProgression(uuid)).thenThrow(new UserNotLoadedException()).thenReturn(team);
        assertThrows(UserNotLoadedException.class, () -> tab.showTab(player));
        assertFalse(tab.isShownTo(player));
        tab.showTab(player); drain();
        assertTrue(tab.isShownTo(player));
        assertEquals(1, sent.size());
    }
    @Test public void unchangedAndProgressOnlyUpdatesKeepDefinitionsCached() {
        tab.showTab(player); drain();
        for (int i = 0; i < 20; i++) { tab.updateAdvancementsToTeam(team); drain(); }
        assertEquals(1, sent.size());
        progress = 1; tab.updateAdvancementsToTeam(team); drain();
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).added.isEmpty());
        assertEquals(Map.of(wrapper, 1), sent.get(1).progress);
        assertTrue(sent.get(1).removed.isEmpty());
    }
    @Test public void resetResendsDefinitionsWithoutStaleRemovals() {
        tab.showTab(player); drain();
        tab.updateAdvancementsToTeam(team); tasks.remove().run();
        tab.resetClientAdvancements(); drain();
        assertEquals(2, sent.size());
        assertEquals(Map.of(wrapper, 0), sent.get(1).added);
        assertTrue(sent.get(1).removed.isEmpty());
        assertTrue(tab.isShownTo(player));
    }
    @Test public void hiddenNodesAndRebuiltNamespacesRemoveOnlyKnownKeys() {
        tab.showTab(player); drain();
        var oldKey = mock(MinecraftKeyWrapper.class);
        tab.restoreClientKeys(Map.of(uuid, Set.of(oldKey)));
        tab.forceUpdateAdvancements(player); drain();
        assertEquals(Set.of(oldKey), sent.get(1).removed);
        visible = false; tab.updateAdvancementsToTeam(team); drain();
        assertEquals(Set.of(wrapper.getKey()), sent.get(2).removed);
        tab.resetClientAdvancements(); drain();
        assertEquals(3, sent.size());
    }
}
