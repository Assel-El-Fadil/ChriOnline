package Shared.Security;

/**
 * Centralized cryptographic constants used by both server and client.
 * All members (M1, M2, M3) reference this class for consistent crypto parameters.
 */
public final class CryptoConfig {

    private CryptoConfig() {} // Utility class — no instantiation

    // ── AES Configuration ───────────────────────────────────────────
    public static final String AES_ALGORITHM   = "AES/GCM/NoPadding";
    public static final int    AES_KEY_SIZE    = 256;   // bits
    public static final int    GCM_IV_LENGTH   = 12;    // bytes (96 bits, NIST recommended)
    public static final int    GCM_TAG_LENGTH  = 128;   // bits

    // ── RSA Configuration ───────────────────────────────────────────
    public static final String RSA_ALGORITHM   = "RSA";
    public static final int    RSA_KEY_SIZE    = 2048;  // bits

    // ── KeyStore / TrustStore ───────────────────────────────────────
    public static final String KEYSTORE_PATH     = "keystore.jks";
    public static final String KEYSTORE_PASSWORD  = "123456";
    public static final String KEYSTORE_ALIAS     = "ecommerce";

    public static final String TRUSTSTORE_PATH     = "truststore.jks";
    public static final String TRUSTSTORE_PASSWORD  = "123456";

    // ── TLS Configuration ───────────────────────────────────────────
    public static final int    SSL_PORT        = 8084;  // Same port, now SSL
    public static final String KEYSTORE_TYPE   = "JKS";

    // ── Session Token Hashing ───────────────────────────────────────
    public static final String TOKEN_HASH_ALGORITHM = "SHA-256";
}
