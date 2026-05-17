package Server.security;

import Shared.Security.AESUtil;

import javax.crypto.SecretKey;
import java.util.HashSet;
import java.util.Set;

/**
 * ReplayAttackTest — Manual integration test for replay-attack protection and AES utilities.
 *
 * Run this class directly (no JUnit required).
 * Tests:
 *   TEST 1 — Basic replay detection via ReplayProtection
 *   TEST 2 — IV uniqueness across 1000 AES encryptions
 *   TEST 3 — AES encrypt/decrypt round-trip correctness
 */
public class ReplayAttackTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  ReplayAttackTest — Security Integration Tests  ");
        System.out.println("=================================================\n");

        runTest1();
        runTest2();
        runTest3();

        System.out.println("\n=================================================");
        System.out.println("  All tests completed.");
        System.out.println("=================================================");
    }

    // =========================================================================
    // TEST 1 — Basic replay detection
    // =========================================================================
    private static void runTest1() {
        System.out.println("--- TEST 1: Basic Replay Detection ---");
        try {
            // 1a. Generate a fresh AES key
            SecretKey key = AESUtil.generateKey();

            // 1b. Encrypt the payment message
            String plaintext = "CHECKOUT|token|CARD|1234";
            String encoded = AESUtil.encrypt(plaintext, key);

            // 1c. Extract the IV part (everything before the first ":")
            String ivBase64 = encoded.split(":", 2)[0];

            // 1d. Get a fresh ReplayProtection instance (reset between runs)
            ReplayProtection rp = ReplayProtection.getInstance();
            rp.clearForTesting();

            // 1e. First registration — must NOT be a replay
            if (rp.isReplay(ivBase64)) {
                System.out.println("TEST 1 FAILED: First registration incorrectly flagged as replay");
            } else {
                rp.register(ivBase64);
            }

            // 1f. Re-register the same IV — must be detected as a replay
            if (rp.isReplay(ivBase64)) {
                System.out.println("TEST 1 PASSED: Replay detected");
            } else {
                System.out.println("TEST 1 FAILED: Replay was NOT detected");
            }

            // 1g. Generate a fresh IV via a new encryption call
            String freshEncoded = AESUtil.encrypt(plaintext, key);
            String freshIV = freshEncoded.split(":", 2)[0];

            if (!rp.isReplay(freshIV)) {
                System.out.println("TEST 1 PASSED: Fresh message accepted");
            } else {
                System.out.println("TEST 1 FAILED: Fresh IV incorrectly flagged as replay");
            }

        } catch (Exception e) {
            System.out.println("TEST 1 ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 2 — IV uniqueness across 1000 encryptions
    // =========================================================================
    private static void runTest2() {
        System.out.println("--- TEST 2: IV Uniqueness Across 1000 Encryptions ---");
        try {
            SecretKey key = AESUtil.generateKey();
            String plaintext = "CHECKOUT|token|CARD|1234";

            Set<String> ivSet = new HashSet<>();
            boolean duplicateFound = false;

            for (int i = 0; i < 1000; i++) {
                String encoded = AESUtil.encrypt(plaintext, key);
                String iv = encoded.split(":", 2)[0];

                if (!ivSet.add(iv)) {   // HashSet.add() returns false on duplicate
                    duplicateFound = true;
                    System.out.println("TEST 2 FAILED: Duplicate IV found at iteration " + i);
                    break;
                }
            }

            if (!duplicateFound) {
                System.out.println("TEST 2 PASSED: All 1000 IVs are unique");
            }

        } catch (Exception e) {
            System.out.println("TEST 2 ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 3 — AES encrypt/decrypt round-trip
    // =========================================================================
    private static void runTest3() {
        System.out.println("--- TEST 3: AES Encrypt/Decrypt Round-Trip ---");
        try {
            SecretKey key = AESUtil.generateKey();
            String original = "CHECKOUT|token123|CARD|4111111111111111|123|12/26";

            String encrypted = AESUtil.encrypt(original, key);
            String decrypted = AESUtil.decrypt(encrypted, key);

            if (original.equals(decrypted)) {
                System.out.println("TEST 3 PASSED: Roundtrip successful");
                System.out.println("  Original : " + original);
                System.out.println("  Encrypted: " + encrypted);
                System.out.println("  Decrypted: " + decrypted);
            } else {
                System.out.println("TEST 3 FAILED: Decrypted text does not match original");
                System.out.println("  Expected : " + original);
                System.out.println("  Got      : " + decrypted);
            }

        } catch (Exception e) {
            System.out.println("TEST 3 FAILED: Exception during roundtrip — " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
}
