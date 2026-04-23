package Shared.Security;

import java.security.KeyPair;
import java.util.Base64;

public class SecurityDemo {
    public static void main(String[] args) {
        try {
            System.out.println("--- RSA Challenge-Response Demo ---");

            System.out.println("[Step 3] Generating Key Pair...");
            KeyPair pair = RSAKeyPairGenerator.generateKeyPair();
            System.out.println("Public Key: " + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()).substring(0, 50) + "...");

            System.out.println("[Step 5] Generating Challenge...");
            String challenge = ChallengeGenerator.generateChallenge();
            System.out.println("Challenge: " + challenge);

            System.out.println("[Step 6] Signing Challenge (Client side)...");
            byte[] signature = Signer.sign(challenge, pair.getPrivate());
            System.out.println("Signature length: " + signature.length + " bytes");

            System.out.println("[Step 7] Verifying Signature (Server side)...");
            boolean isValid = Verifier.verify(challenge, signature, pair.getPublic());
            
            if (isValid) {
                System.out.println("SUCCESS: Signature is valid!");
            } else {
                System.out.println("FAILURE: Signature is invalid!");
            }

        } catch (Exception e) {
            System.err.println("An error occurred during verification:");
            e.printStackTrace();
        }
    }
}
