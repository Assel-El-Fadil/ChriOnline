package Shared.Security;

import java.security.*;

public class Verifier {
    
    public static boolean verify(String challenge, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(challenge.getBytes());
        return signature.verify(signatureBytes);
    }
}
