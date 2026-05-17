package Shared.Security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HMACUtil {

    public static final ThreadLocal<SecretKey> currentKey = new ThreadLocal<>();

    private static final String ALGORITHM = "HmacSHA256";

    public static String compute(String message, SecretKey key) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(key);
        byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }

    public static boolean verify(String message, String hmac, SecretKey key) {
        try {
            String computedHmac = compute(message, key);
            return computedHmac.equals(hmac);
        } catch (Exception e) {
            return false;
        }
    }
}
