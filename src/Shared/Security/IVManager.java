package Shared.Security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IVManager — Thread-safe utility for AES/GCM Initialization Vector (IV) management.
 *
 * Responsibilities:
 *   - Generate cryptographically secure 12-byte IVs (NIST recommended for GCM)
 *   - Encode/decode IVs to/from Base64 for transport/storage
 *   - Maintain a thread-safe in-memory IV registry (ConcurrentHashMap)
 *
 * Rules:
 *   - Always uses SecureRandom, never java.util.Random
 *   - IV size is sourced from CryptoConfig.GCM_IV_LENGTH (12 bytes)
 *   - All operations are thread-safe
 */
public class IVManager {

    // ── Thread-safe IV registry (sessionId / label → Base64-encoded IV) ──────
    private static final ConcurrentHashMap<String, String> ivRegistry =
            new ConcurrentHashMap<>();

    // Single shared SecureRandom instance — thread-safe by spec (synchronized internally)
    private static final SecureRandom secureRandom = new SecureRandom();

    // Utility class — no instantiation
    private IVManager() {}

    // -------------------------------------------------------------------------
    // generateIV() — fresh random 12-byte array via SecureRandom
    // -------------------------------------------------------------------------
    /**
     * Generates a cryptographically secure 12-byte (96-bit) IV.
     * A new IV is produced on every call — never reuse IVs with the same key.
     *
     * @return a fresh 12-byte IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[CryptoConfig.GCM_IV_LENGTH]; // 12 bytes
        secureRandom.nextBytes(iv);
        return iv;
    }

    // -------------------------------------------------------------------------
    // toBase64(byte[] iv) — encode IV bytes to Base64 string
    // -------------------------------------------------------------------------
    /**
     * Encodes an IV byte array to a Base64 string for safe transport or storage.
     *
     * @param iv the IV byte array (must be non-null)
     * @return Base64-encoded string representation of the IV
     * @throws IllegalArgumentException if iv is null
     */
    public static String toBase64(byte[] iv) {
        if (iv == null) {
            throw new IllegalArgumentException("IV must not be null");
        }
        return Base64.getEncoder().encodeToString(iv);
    }

    // -------------------------------------------------------------------------
    // fromBase64(String encoded) — decode Base64 string back to byte array
    // -------------------------------------------------------------------------
    /**
     * Decodes a Base64-encoded IV string back to its original byte array.
     *
     * @param encoded Base64 string (must be non-null and non-empty)
     * @return decoded IV byte array
     * @throws IllegalArgumentException if encoded is null or empty
     */
    public static byte[] fromBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded IV must not be null or empty");
        }
        return Base64.getDecoder().decode(encoded);
    }

    // =========================================================================
    // Registry operations (thread-safe via ConcurrentHashMap)
    // =========================================================================

    /**
     * Registers a newly generated IV under the given session/label key.
     * Generates a fresh IV, stores it in the registry, and returns the IV bytes.
     *
     * @param sessionKey unique identifier for this IV entry (e.g. session ID, message ID)
     * @return the generated IV bytes
     */
    public static byte[] registerIV(String sessionKey) {
        byte[] iv = generateIV();
        ivRegistry.put(sessionKey, toBase64(iv));
        return iv;
    }

    /**
     * Retrieves the IV associated with the given session/label key.
     *
     * @param sessionKey unique identifier used when the IV was registered
     * @return the IV byte array, or null if not found
     */
    public static byte[] getIV(String sessionKey) {
        String encoded = ivRegistry.get(sessionKey);
        return (encoded != null) ? fromBase64(encoded) : null;
    }

    /**
     * Removes an IV from the registry once it is no longer needed.
     * Call this after decryption to avoid stale IV accumulation.
     *
     * @param sessionKey the key under which the IV was registered
     */
    public static void removeIV(String sessionKey) {
        ivRegistry.remove(sessionKey);
    }

    /**
     * Returns the current number of IVs held in the registry.
     * Useful for monitoring or testing.
     *
     * @return registry size
     */
    public static int registrySize() {
        return ivRegistry.size();
    }
}
