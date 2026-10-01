package com.fren_gor.ultimateAdvancementAPI.commands;
import com.fren_gor.ultimateAdvancementAPI.*;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.tests.Utils;
import com.fren_gor.ultimateAdvancementAPI.util.SchedulerSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CommandsCommonTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<SchedulerSupport> scheduler;
    private final Deque<Task> tasks = new ArrayDeque<>();
    private record Task(Player owner, Runnable operation) {}
    private Player target, sender, owner;
    private Advancement advancement;
    private AdvancementTab tab;
    private CommandsCommon<Exception> commands;
    @Before public void setup() {
        bukkit = Utils.mockServer();
        scheduler = mockStatic(SchedulerSupport.class);
        target = mock(Player.class); sender = mock(Player.class);
        scheduler.when(() -> SchedulerSupport.owns(any())).thenAnswer(invocation -> owner == invocation.getArgument(0));
        scheduler.when(() -> SchedulerSupport.player(any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            tasks.add(new Task(invocation.getArgument(1), invocation.getArgument(3))); return null;
        });
        AdvancementMain main = mock(AdvancementMain.class);
        when(main.getOwningPlugin()).thenReturn(mock(Plugin.class));
        tab = mock(AdvancementTab.class);
        when(tab.isActive()).thenReturn(true);
        advancement = mock(Advancement.class);
        when(advancement.getAdvancementTab()).thenReturn(tab);
        when(tab.getAdvancements()).thenReturn(List.of(advancement));
        when(main.getTabs()).thenReturn(List.of(tab));
        commands = new CommandsCommon<>(main, Exception::new);
    }
    @After public void close() {
        if (scheduler != null) scheduler.close();
        if (bukkit != null) bukkit.close();
    }
    private void drain() { while (!tasks.isEmpty()) { var task=tasks.remove(); owner=task.owner; task.operation.run(); } }
    @Test public void grantsRunOnTheTargetAndRepliesRunOnTheSender() throws Exception {
        doAnswer(invocation -> { assertSame(target, owner); return null; }).when(advancement).grant(target, false);
        doAnswer(invocation -> { assertSame(sender, owner); return null; }).when(sender).sendMessage(anyString());
        commands.grantOne(sender, advancement, target, false);
        verify(advancement, never()).grant(any(), anyBoolean());
        assertEquals(1, tasks.size());
        drain();
        verify(advancement).grant(target, false);
        verify(sender).sendMessage(anyString());
    }
    @Test public void bulkCommandsQueueOneTaskPerTarget() throws Exception {
        commands.grantAll(sender, List.of(target), false);
        assertEquals(1, tasks.size());
        drain();
        verify(advancement).grant(target, false);
    }
    @Test public void inactiveTabsFailBeforeScheduling() {
        when(tab.isActive()).thenReturn(false);
        assertThrows(Exception.class, () -> commands.grantTab(sender, tab, target, false));
        assertTrue(tasks.isEmpty());
    }
}
