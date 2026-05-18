package Shared.Security;

import java.security.*;
import java.security.spec.*;
import java.security.cert.Certificate;
import java.io.*;
import java.util.Base64;
import java.util.Scanner;

public class RSAKeyPairGenerator {

    public static PublicKey loadPublicKeyFromString(String b64) throws Exception {
        String cleanB64 = b64.replaceAll("\\s", ""); 
        byte[] decoded = Base64.getDecoder().decode(cleanB64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("=== ChriOnline Admin Key Generator ===");
            System.out.print("Enter a strong password for your new admin keystore (.p12): ");
            String password = scanner.nextLine().trim();

            if (password.isEmpty()) {
                System.out.println("Password cannot be empty.");
                return;
            }

            File p12File = new File("admin_keys.p12");
            if (p12File.exists()) {
                System.out.println("admin_keys.p12 already exists. Please delete it or back it up first.");
                return;
            }

            System.out.println("Generating RSA Key Pair and self-signed certificate inside admin_keys.p12...");
            
            // Generate using keytool
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-alias", "admin",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", "admin_keys.p12",
                    "-storepass", password,
                    "-keypass", password,
                    "-validity", "3650",
                    "-dname", "CN=ChriOnline-Admin"
            );
            
            pb.inheritIO(); // Show output if any
            Process p = pb.start();
            int exitCode = p.waitFor();
            
            if (exitCode != 0) {
                System.err.println("Failed to generate keystore. Keytool exit code: " + exitCode);
                return;
            }

            System.out.println("Keystore generated successfully!");

            // Now extract the public key so the admin can copy it to the database
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream("admin_keys.p12")) {
                ks.load(fis, password.toCharArray());
            }
            
            Certificate cert = ks.getCertificate("admin");
            PublicKey publicKey = cert.getPublicKey();
            
            String b64PublicKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            System.out.println("\n=======================================================");
            System.out.println("ADMIN PUBLIC KEY (Copy and paste into your Database):");
            System.out.println("=======================================================");
            System.out.println(b64PublicKey);
            System.out.println("=======================================================\n");
            
            System.out.println("Done! Keep your 'admin_keys.p12' safe and remember your password.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
