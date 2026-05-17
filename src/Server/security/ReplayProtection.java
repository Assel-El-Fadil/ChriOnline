package Server.security;

import Shared.Security.CryptoConfig;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ReplayProtection — Server-side defence against IV/message replay attacks.
 *
 * How it works:
 *   - Every incoming encrypted message carries a Base64-encoded IV.
 *   - Before decryption the server calls isReplay(ivBase64).
 *     → If true  → reject the message (already processed within the last 5 min).
 *     → If false → call register(ivBase64) to mark it as seen, then decrypt.
 *   - cleanup() is called automatically inside register() to evict stale entries
 *     and keep memory bounded.
 *
 * Thread-safety:
 *   - All state is held in a ConcurrentHashMap — safe for concurrent server threads.
 *   - Singleton pattern with eager initialisation.
 */
public class ReplayProtection {

    // ── Constants ──────────────────────────────────────────────────────────────
    /** Window during which a duplicate IV is considered a replay (5 minutes). */
    private static final long WINDOW_MS = 5 * 60 * 1000L; // 5 minutes in milliseconds

    // ── IV registry: Base64(IV) → timestamp of first receipt ─────────────────
    private final ConcurrentHashMap<String, Long> seenIVs = new ConcurrentHashMap<>();

    // ── Singleton ──────────────────────────────────────────────────────────────
    private static final ReplayProtection INSTANCE = new ReplayProtection();

    private ReplayProtection() {}

    public static ReplayProtection getInstance() {
        return INSTANCE;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Checks whether this IV has already been seen in the last 5 minutes.
     *
     * @param ivBase64 Base64-encoded IV from the incoming message
     * @return {@code true} if this is a replay (IV already registered within the window);
     *         {@code false} if this is a fresh, unseen IV
     * @throws IllegalArgumentException if ivBase64 is null or empty
     */
    public boolean isReplay(String ivBase64) {
        if (ivBase64 == null || ivBase64.isEmpty()) {
            throw new IllegalArgumentException("IV must not be null or empty");
        }

        Long timestamp = seenIVs.get(ivBase64);
        if (timestamp == null) {
            return false; // Never seen — not a replay
        }

        // Seen before: check if still within the 5-minute window
        return (System.currentTimeMillis() - timestamp) < WINDOW_MS;
    }

    /**
     * Registers an IV as seen at the current time, then triggers cleanup of
     * expired entries to keep memory usage bounded.
     *
     * Call this immediately after confirming the IV is not a replay and before
     * (or after) decryption succeeds.
     *
     * @param ivBase64 Base64-encoded IV to register
     * @throws IllegalArgumentException if ivBase64 is null or empty
     */
    public void register(String ivBase64) {
        if (ivBase64 == null || ivBase64.isEmpty()) {
            throw new IllegalArgumentException("IV must not be null or empty");
        }

        seenIVs.put(ivBase64, System.currentTimeMillis());

        // Proactively evict entries older than the replay window
        cleanup();
    }

    /**
     * Removes all IV entries whose recorded timestamp is older than 5 minutes.
     * Called automatically by {@link #register(String)}.
     *
     * May also be called manually (e.g. from a scheduled maintenance task).
     */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        seenIVs.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    // ── Monitoring helpers (useful for tests / admin endpoints) ───────────────

    /**
     * Returns the number of IVs currently tracked in the registry.
     * Useful for health checks and unit tests.
     *
     * @return current registry size
     */
    public int size() {
        return seenIVs.size();
    }

    /**
     * Clears all tracked IVs.
     * Intended for testing only — do NOT call in production.
     */
    public void clearForTesting() {
        seenIVs.clear();
    }
}
