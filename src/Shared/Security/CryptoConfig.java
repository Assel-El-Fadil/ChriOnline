package Shared.Security;

/**
 * Centralized cryptographic constants used by both server and client.
 * All members (M1, M2, M3) reference this class for consistent crypto parameters.
 */
public final class CryptoConfig {

    private CryptoConfig() {} // Utility class — no instantiation

    // ── AES Configuration ───────────────────────────────────────────
    public static final String AES_ALGORITHM   = "AES/GCM/NoPadding";
    public static final int    AES_KEY_SIZE    = 256;
    public static final int    GCM_IV_LENGTH   = 12;
    public static final int    GCM_TAG_LENGTH  = 128;
    public static final String HMAC_ALGORITHM  = "HmacSHA256";
    public static final String IV_SEPARATOR    = ":";

    // ── RSA Configuration ───────────────────────────────────────────
    public static final String RSA_ALGORITHM         = "RSA";
    public static final String RSA_TRANSFORMATION    = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    public static final int    RSA_KEY_SIZE    = 2048;

    // ── KeyStore / TrustStore ───────────────────────────────────────
    public static final String KEYSTORE_PATH     = "keystore.p12";
    public static final String KEYSTORE_PASSWORD  = "123456";
    public static final String KEYSTORE_ALIAS     = "ecommerce";

    public static final String TRUSTSTORE_PATH     = "truststore.p12";
    public static final String TRUSTSTORE_PASSWORD  = "123456";

    // ── TLS Configuration ───────────────────────────────────────────
    public static final int    SSL_PORT        = 8084;
    public static final String KEYSTORE_TYPE   = "PKCS12";

    // ── Session Token Hashing ───────────────────────────────────────
    public static final String TOKEN_HASH_ALGORITHM = "SHA-256";
}
