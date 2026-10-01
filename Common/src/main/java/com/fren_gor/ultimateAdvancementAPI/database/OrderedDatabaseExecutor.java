package com.fren_gor.ultimateAdvancementAPI.database;

import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns a database connection, orders operations and batches consecutive advancement writes. */
public final class OrderedDatabaseExecutor implements Executor {
    public static final int DEFAULT_CAPACITY = 8192;
    public static final int DEFAULT_BATCH_SIZE = 128;

    private final IDatabase database;
    private final Logger logger;
    private final ArrayBlockingQueue<Runnable> queue;
    private final Object admission = new Object();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final int batchSize;
    private final Thread worker;
    private volatile boolean accepting = true;

    public OrderedDatabaseExecutor(IDatabase database, Logger logger, String name) {
        this(database, logger, name, DEFAULT_CAPACITY, DEFAULT_BATCH_SIZE);
    }

    public OrderedDatabaseExecutor(IDatabase database, Logger logger, String name, int capacity, int batchSize) {
        if (capacity < 1 || batchSize < 1) throw new IllegalArgumentException("Invalid database queue limits");
        this.database = database;
        this.logger = logger == null ? Logger.getLogger("UltimateAdvancementAPI") : logger;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.batchSize = batchSize;
        worker = new Thread(this::run, name + "-database");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void execute(Runnable command) {
        if (Thread.currentThread() == worker) {
            command.run();
            return;
        }
        synchronized (admission) {
            checkAdmission();
            queue.add(command);
        }
    }

    public ProgressionChange change(AdvancementKey key, TeamProgression team, IntUnaryOperator operation) {
        synchronized (admission) {
            int old = team.getStoredProgression(key);
            int next = operation.applyAsInt(old);
            if (next < 0) throw new IllegalArgumentException("Progression cannot be negative");
            if (next == old) return new ProgressionChange(old, next, CompletableFuture.completedFuture(Result.SUCCESSFUL));
            // Reject before modifying the cache; database saturation must not create unsaved progress.
            checkAdmission();
            CompletableFuture<Result> persisted = new CompletableFuture<>();
            team.updateProgression(key, next);
            queue.add(new Write(new AdvancementUpdate(key, team.getTeamId(), next), persisted));
            return new ProgressionChange(old, next, persisted);
        }
    }

    private void checkAdmission() {
        if (!accepting) throw new RejectedExecutionException("The advancement database is shutting down");
        if (queue.remainingCapacity() == 0) throw new RejectedExecutionException("The advancement database queue is full");
    }

    public int pendingOperations() { return queue.size(); }

    /** Stops admission and drains accepted operations before the owning worker closes the database. */
    public boolean shutdown(long timeout, TimeUnit unit) {
        synchronized (admission) { accepting = false; }
        if (Thread.currentThread() == worker) return false;
        try {
            return stopped.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void run() {
        try {
            while (accepting || !queue.isEmpty()) {
                Runnable next = queue.poll(20, TimeUnit.MILLISECONDS);
                if (next == null) continue;
                if (next instanceof Write first) {
                    // Give writes submitted together a short window to form a transaction.
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    List<Write> writes = new ArrayList<>();
                    writes.add(first);
                    while (writes.size() < batchSize && queue.peek() instanceof Write) {
                        writes.add((Write) queue.poll());
                    }
                    persist(writes);
                } else {
                    try { next.run(); }
                    catch (Throwable error) { logger.log(Level.SEVERE, "Advancement database operation failed", error); }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Advancement database worker interrupted", e);
        } finally {
            try { database.close(); }
            catch (Exception error) { logger.log(Level.SEVERE, "Cannot close advancement database", error); }
            stopped.countDown();
        }
    }

    private void persist(List<Write> writes) {
        Result result;
        try {
            // Preserve revocations: deleting progress must still clear pending rewards before a re-grant.
            database.updateAdvancements(writes.stream().map(Write::update).toList());
            result = Result.SUCCESSFUL;
        } catch (Exception error) {
            logger.log(Level.SEVERE, "Cannot persist advancement batch of " + writes.size() + " operations", error);
            result = new Result(error);
        }
        for (Write write : writes) write.persisted.complete(result);
    }

    private record Write(AdvancementUpdate update, CompletableFuture<Result> persisted) implements Runnable {
        @Override public void run() { throw new UnsupportedOperationException("Writes are executed in a batch"); }
    }
}
