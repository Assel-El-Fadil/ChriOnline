package Shared.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AESUtil {

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    IV_SIZE        = 12;   // bytes
    private static final int    TAG_BIT_LENGTH = 128;  // bits
    private static final SecureRandom secureRandom = new SecureRandom();

    // -------------------------------------------------------------------------
    // generateKey() — random 256-bit AES SecretKey
    // -------------------------------------------------------------------------
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, secureRandom);
        return keyGen.generateKey();
    }

    // -------------------------------------------------------------------------
    // encrypt(String plaintext, SecretKey key)
    // AES/GCM/NoPadding, fresh random 12-byte IV per call
    // Returns Base64(IV) + ":" + Base64(CIPHERTEXT)
    // -------------------------------------------------------------------------
    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        String encodedIV         = Base64.getEncoder().encodeToString(iv);
        String encodedCiphertext  = Base64.getEncoder().encodeToString(ciphertext);
        return encodedIV + ":" + encodedCiphertext;
    }

    // -------------------------------------------------------------------------
    // decrypt(String encoded, SecretKey key)
    // Splits on ":", decodes IV and ciphertext, decrypts, returns plaintext
    // -------------------------------------------------------------------------
    public static String decrypt(String encoded, SecretKey key) throws Exception {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encoded format: expected 'IV:CIPHERTEXT'");
        }

        byte[] iv         = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // encodeKey(SecretKey key) — Base64 string
    // -------------------------------------------------------------------------
    public static String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // -------------------------------------------------------------------------
    // decodeKey(String encoded) — reconstructs SecretKey from Base64
    // -------------------------------------------------------------------------
    public static SecretKey decodeKey(String encoded) {
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
