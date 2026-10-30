package com.donutspawnerhelper;

import java.util.OptionalInt;

/**
 * Tracks server-confirmed stack messages and suppresses duplicates.
 * Pure Java (no Minecraft classes). Time is injected so tests are deterministic.
 */
public final class StackTracker {

    public enum Outcome {
        /** Not a stack confirmation; do nothing. */
        IGNORED,
        /** Same confirmation repeated within the duplicate window; do nothing. */
        DUPLICATE,
        /** Valid confirmation; update count but stay quiet (rate limit). */
        ACCEPTED_SILENT,
        /** Valid confirmation; show the chat message. */
        ACCEPTED
    }

    /** Identical confirmations (same count) closer than this are treated as one. */
    static final long DUPLICATE_WINDOW_MS = 400;
    /** Minimum gap between two chat messages we print. */
    static final long MIN_PRINT_GAP_MS = 250;
    /** A spawner interaction counts as "the one that was confirmed" for this long. */
    static final long BIND_WINDOW_MS = 3000;

    private int lastCount = -1;
    private long lastAcceptedAt = Long.MIN_VALUE / 2;
    private long lastPrintedAt = Long.MIN_VALUE / 2;

    private boolean hasPending;
    private long pendingPos;
    private long pendingAt;

    private boolean hasConfirmed;
    private long confirmedPos;
    private int confirmedCount;

    /** Remember which spawner the player just interacted with. */
    public void bind(long packedPos, long nowMs) {
        hasPending = true;
        pendingPos = packedPos;
        pendingAt = nowMs;
    }

    public Outcome onServerMessage(String plainText, long nowMs) {
        OptionalInt parsed = StackMessageParser.parseStackAdded(plainText);
        if (parsed.isEmpty()) return Outcome.IGNORED;
        int count = parsed.getAsInt();

        if (count == lastCount && nowMs - lastAcceptedAt < DUPLICATE_WINDOW_MS) {
            return Outcome.DUPLICATE;
        }
        lastCount = count;
        lastAcceptedAt = nowMs;

        if (hasPending && nowMs - pendingAt <= BIND_WINDOW_MS) {
            hasConfirmed = true;
            confirmedPos = pendingPos;
            confirmedCount = count;
        }

        if (nowMs - lastPrintedAt < MIN_PRINT_GAP_MS) {
            return Outcome.ACCEPTED_SILENT;
        }
        lastPrintedAt = nowMs;
        return Outcome.ACCEPTED;
    }

    /** Latest server-confirmed stack size, or -1. */
    public int lastCount() {
        return lastCount;
    }

    /** Server-confirmed stack size for this exact spawner position, if we know it. */
    public OptionalInt confirmedCountAt(long packedPos) {
        return hasConfirmed && confirmedPos == packedPos ? OptionalInt.of(confirmedCount) : OptionalInt.empty();
    }
}
