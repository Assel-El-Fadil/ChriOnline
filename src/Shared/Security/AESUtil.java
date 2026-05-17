package Shared.Security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;

public class AESUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; 
    private static final int TAG_BIT_LENGTH = 128; 
    private static final SecureRandom secureRandom = new SecureRandom();

    public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ciphertext = cipher.doFinal(data);
        
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return combined;
    }

    public static byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(data, 0, iv, 0, iv.length);
        byte[] ciphertext = new byte[data.length - iv.length];
        System.arraycopy(data, iv.length, ciphertext, 0, ciphertext.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }
}
