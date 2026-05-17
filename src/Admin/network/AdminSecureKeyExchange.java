package Admin.network;

import Shared.Security.RSAUtil;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class AdminSecureKeyExchange {

    public SecretKey performKeyExchange(BufferedReader reader, PrintWriter writer) throws Exception {
        // 1. Generate a random 256-bit AES key using KeyGenerator.getInstance("AES")
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey aesKey = keyGen.generateKey();

        // 2. Receive the server's RSA public key (sent as Base64-encoded bytes over the socket)
        String line = reader.readLine();
        if (line == null || !line.startsWith("SERVER_PUBLIC_KEY:")) {
            throw new AdminNetworkException("Invalid handshake response from server: " + line);
        }
        String publicKeyB64 = line.substring("SERVER_PUBLIC_KEY:".length());
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyB64);
        
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey serverPublicKey = keyFactory.generatePublic(keySpec);

        // 3. Encrypt the AES key using RSAUtil.encrypt(aesKey.getEncoded(), serverPublicKey)
        byte[] encryptedAesKey = RSAUtil.encrypt(aesKey.getEncoded(), serverPublicKey);

        // 4. Send the encrypted AES key back to the server as a Base64 string
        String encryptedAesKeyB64 = Base64.getEncoder().encodeToString(encryptedAesKey);
        writer.println("AES_KEY:" + encryptedAesKeyB64);

        // Receive the server's HANDSHAKE_OK confirmation
        String confirmation = reader.readLine();
        if (confirmation == null || !confirmation.equals("HANDSHAKE_OK")) {
            throw new AdminNetworkException("Handshake verification failed: " + confirmation);
        }

        return aesKey;
    }
}
