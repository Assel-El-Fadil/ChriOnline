package Shared.Security;

import java.security.*;

public class Signer {

    public static byte[] sign(String challenge, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(challenge.getBytes());
        return signature.sign();
    }
}
