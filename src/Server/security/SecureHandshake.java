package Server.security;

import Shared.Security.CryptoConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.security.*;
import java.util.Base64;

/**
 * Server-side secure handshake — establishes an AES session key on top of TLS.
 *
 * Protocol (after TLS connection is already established):
 *   1. Server loads its RSA key pair from the KeyStore
 *   2. Server sends its RSA public key (Base64) to the client
 *   3. Client generates a random AES-256 key, encrypts it with the RSA public key,
 *      and sends the Base64 ciphertext back
 *   4. Server decrypts the AES key with its RSA private key
 *   5. Both sides now share the same AES session key for application-layer encryption
 */
public class SecureHandshake {

    private static final Logger logger = LogManager.getLogger(SecureHandshake.class);

    /**
     * Performs the server side of the handshake.
     *
     * @param reader  BufferedReader connected to the client
     * @param writer  PrintWriter connected to the client
     * @param keyPair the server's RSA KeyPair (loaded from keystore)
     * @return the negotiated AES SecretKey, or null if handshake failed
     */
    public static SecretKey perform(BufferedReader reader, PrintWriter writer, KeyPair keyPair) {
        try {
            // ── Step 1: Send RSA public key to the client ────────────────
            PublicKey publicKey = keyPair.getPublic();
            String publicKeyB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            writer.println("SERVER_PUBLIC_KEY:" + publicKeyB64);
            logger.info("[SecureHandshake] Sent RSA public key to client");

            // ── Step 2: Receive the AES key (encrypted with RSA) ─────────
            String clientMessage = reader.readLine();
            if (clientMessage == null || !clientMessage.startsWith("AES_KEY:")) {
                logger.error("[SecureHandshake] Invalid handshake message from client: " + clientMessage);
                return null;
            }

            String encryptedAesKeyB64 = clientMessage.substring("AES_KEY:".length());
            byte[] encryptedAesKey = Base64.getDecoder().decode(encryptedAesKeyB64);

            // ── Step 3: Decrypt the AES key with RSA private key ─────────
            Cipher rsaCipher = Cipher.getInstance(CryptoConfig.RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);

            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // ── Step 4: Confirm handshake success ────────────────────────
            writer.println("HANDSHAKE_OK");
            logger.info("[SecureHandshake] AES session key established successfully ("
                    + (aesKeyBytes.length * 8) + "-bit)");

            return aesKey;

        } catch (Exception e) {
            logger.error("[SecureHandshake] Handshake failed: " + e.getMessage(), e);
            try {
                writer.println("HANDSHAKE_FAIL");
            } catch (Exception ignored) {}
            return null;
        }
    }

    /**
     * Loads the RSA KeyPair from the configured KeyStore.
     *
     * @return the RSA KeyPair (public + private key)
     * @throws Exception if the keystore cannot be loaded or the alias is missing
     */
    public static KeyPair loadKeyPairFromKeyStore() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance(CryptoConfig.KEYSTORE_TYPE);

        try (java.io.FileInputStream fis = new java.io.FileInputStream(CryptoConfig.KEYSTORE_PATH)) {
            ks.load(fis, CryptoConfig.KEYSTORE_PASSWORD.toCharArray());
        }

        Key key = ks.getKey(CryptoConfig.KEYSTORE_ALIAS,
                CryptoConfig.KEYSTORE_PASSWORD.toCharArray());
        if (!(key instanceof PrivateKey)) {
            throw new KeyException("No private key found for alias: " + CryptoConfig.KEYSTORE_ALIAS);
        }

        PrivateKey privateKey = (PrivateKey) key;
        java.security.cert.Certificate cert = ks.getCertificate(CryptoConfig.KEYSTORE_ALIAS);
        PublicKey publicKey = cert.getPublicKey();

        return new KeyPair(publicKey, privateKey);
    }
}
