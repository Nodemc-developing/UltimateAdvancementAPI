package com.fren_gor.ultimateAdvancementAPI.database;

import com.fren_gor.ultimateAdvancementAPI.database.impl.SQLite;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrderedDatabaseExecutorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static AdvancementKey KEY;
    @BeforeClass public static void initializeServer() {
        com.fren_gor.ultimateAdvancementAPI.tests.Utils.mockServer(() -> KEY = new AdvancementKey("test", "progress"));
    }
    private static final Logger LOG = Logger.getLogger("database-test");

    private TeamProgression newTeam() throws Exception {
        SQLite database = new SQLite(temporary.newFile(), LOG);
        database.setUp();
        try { return database.loadOrRegisterPlayer(UUID.randomUUID(), "player").getKey(); }
        finally { database.close(); }
    }

    @Test public void shutdownDrainsWritesBeforeClosing() throws Exception {
        var file = temporary.newFile();
        SQLite database = new SQLite(file, LOG);
        database.setUp();
        UUID player = UUID.randomUUID();
        TeamProgression team = database.loadOrRegisterPlayer(player, "player").getKey();
        OrderedDatabaseExecutor executor = new OrderedDatabaseExecutor(database, LOG, "test");
        List<CompletableFuture<Result>> writes = new ArrayList<>();
        for (int i = 1; i <= 300; i++) writes.add(executor.change(KEY, team, old -> old + 1).persisted());
        assertTrue(executor.shutdown(10, TimeUnit.SECONDS));
        for (var future : writes) assertSame(Result.SUCCESSFUL, future.get(1, TimeUnit.SECONDS));
        SQLite reopened = new SQLite(file, LOG);
        try { assertEquals(300, reopened.loadUUID(player).getStoredProgression(KEY)); } finally { reopened.close(); }
    }

    @Test public void saturationRejectsBeforeChangingCache() throws Exception {
        IDatabase database = mock(IDatabase.class);
        OrderedDatabaseExecutor executor = new OrderedDatabaseExecutor(database, LOG, "test", 2, 2);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        executor.execute(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); } });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        TeamProgression team = newTeam();
        try {
            executor.execute(() -> {});
            executor.execute(() -> {});
            assertThrows(RejectedExecutionException.class, () -> executor.change(KEY, team, old -> old + 1));
            assertEquals(0, team.getStoredProgression(KEY));
        } finally { release.countDown(); assertTrue(executor.shutdown(5, TimeUnit.SECONDS)); }
    }

    @Test public void concurrentRegionsDoNotLoseIncrements() throws Exception {
        IDatabase database = mock(IDatabase.class);
        TeamProgression team = newTeam();
        OrderedDatabaseExecutor executor = new OrderedDatabaseExecutor(database, LOG, "test");
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) results.add(callers.submit(() -> {
                for (int i = 0; i < 500; i++) executor.change(KEY, team, old -> old + 1);
            }));
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
            assertEquals(4000, team.getStoredProgression(KEY));
        } finally { callers.shutdownNow(); assertTrue(executor.shutdown(10, TimeUnit.SECONDS)); }
    }

    @Test public void upsertKeepsPendingRewardsAndRevokeClearsThem() throws Exception {
        SQLite database = new SQLite(temporary.newFile(), LOG);
        database.setUp();
        TeamProgression team = database.loadOrRegisterPlayer(UUID.randomUUID(), "player").getKey();
        try {
            database.updateAdvancement(KEY, team.getTeamId(), 1);
            database.setUnredeemed(KEY, true, team.getTeamId());
            database.updateAdvancements(List.of(new AdvancementUpdate(KEY, team.getTeamId(), 2)));
            assertEquals(1, database.getUnredeemed(team.getTeamId()).size());
            database.updateAdvancements(List.of(new AdvancementUpdate(KEY, team.getTeamId(), 0), new AdvancementUpdate(KEY, team.getTeamId(), 3)));
            assertTrue(database.getUnredeemed(team.getTeamId()).isEmpty());
        } finally { database.close(); }
    }

    @Test public void failedTransactionRollsBackEarlierUpdates() throws Exception {
        SQLite database = new SQLite(temporary.newFile(), LOG);
        database.setUp();
        UUID uuid = UUID.randomUUID();
        TeamProgression team = database.loadOrRegisterPlayer(uuid, "player").getKey();
        try {
            assertThrows(java.sql.SQLException.class, () -> database.updateAdvancements(List.of(new AdvancementUpdate(KEY, team.getTeamId(), 1), new AdvancementUpdate(KEY, Integer.MAX_VALUE, 2))));
            assertEquals(0, database.loadUUID(uuid).getStoredProgression(KEY));
        } finally { database.close(); }
    }

    @Test public void memberCallbackDoesNotHoldMembershipMonitor() throws Exception {
        TeamProgression team = newTeam();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            team.forEachMember(uuid -> {
                try { other.submit(() -> team.addMember(UUID.randomUUID())).get(2, TimeUnit.SECONDS); }
                catch (Exception e) { throw new AssertionError("Membership lock held during callback", e); }
            });
            assertEquals(2, team.getSize());
        } finally { other.shutdownNow(); }
    }
}
