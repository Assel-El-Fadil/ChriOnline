package Shared.Security;

import java.security.*;
import java.security.spec.*;
import java.io.*;
import java.util.Base64;

public class RSAKeyPairGenerator {

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    public static void saveKeyToFile(Key key, String filePath) throws IOException {
        String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(encodedKey);
        }
    }

    public static PrivateKey loadPrivateKeyFromFile(String filePath) throws Exception {
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
        String b64 = content.replaceAll("\\s", ""); 
        byte[] decoded = Base64.getDecoder().decode(b64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public static PublicKey loadPublicKeyFromString(String b64) throws Exception {
        String cleanB64 = b64.replaceAll("\\s", ""); 
        byte[] decoded = Base64.getDecoder().decode(cleanB64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static void main(String[] args) {
        try {
            System.out.println("Generating RSA Key Pair...");
            KeyPair pair = generateKeyPair();
            
            saveKeyToFile(pair.getPublic(), "admin_public.key");
            saveKeyToFile(pair.getPrivate(), "admin_private.key");
            
            System.out.println("Keys generated and saved successfully!");
            System.out.println("Public Key (Base64): " + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
