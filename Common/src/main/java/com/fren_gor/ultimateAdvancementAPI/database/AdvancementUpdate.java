package com.fren_gor.ultimateAdvancementAPI.database;

import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;

/** An immutable database write; a zero progression removes the stored advancement. */
public record AdvancementUpdate(AdvancementKey key, int teamId, int progression) {
}
