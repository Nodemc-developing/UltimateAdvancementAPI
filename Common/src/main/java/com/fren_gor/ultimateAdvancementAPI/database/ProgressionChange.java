package com.fren_gor.ultimateAdvancementAPI.database;

import java.util.concurrent.CompletableFuture;

/** Captures an atomic cache change and acknowledgement of its durable database write. */
public record ProgressionChange(int previous, int current, CompletableFuture<Result> persisted) {
}
