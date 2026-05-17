package Server.service;

import Shared.Security.AESUtil;

import javax.crypto.SecretKey;

/**
 * PaymentEncryptionService — Encrypts and decrypts sensitive payment data
 * before persistence to the database and after retrieval from it.
 *
 * Design rules:
 *   - The AES key is NEVER hardcoded; it is injected via the constructor only.
 *   - Encryption delegates entirely to {@link AESUtil} (AES/GCM/NoPadding,
 *     128-bit tag, fresh random 12-byte IV per call, Base64 "IV:CIPHERTEXT").
 *   - maskCardNumber() is display-only; it performs no encryption.
 *   - This class does NOT touch any DAO, handler, controller, or DB schema.
 */
public class PaymentEncryptionService {

    // ── AES key (256-bit) injected at construction time ───────────────────────
    private final SecretKey aesKey;

    /**
     * Creates a PaymentEncryptionService backed by the provided AES key.
     *
     * @param aesKey a 256-bit AES {@link SecretKey} — must not be null
     * @throws IllegalArgumentException if aesKey is null
     */
    public PaymentEncryptionService(SecretKey aesKey) {
        if (aesKey == null) {
            throw new IllegalArgumentException("AES key must not be null");
        }
        this.aesKey = aesKey;
    }

    // ── Card Number ───────────────────────────────────────────────────────────

    /**
     * Encrypts a raw card number for secure storage in the database.
     *
     * @param cardNumber the plain 16-digit card number string
     * @return Base64-encoded "IV:CIPHERTEXT" string, safe to persist in the DB
     * @throws Exception if AES encryption fails
     */
    public String encryptCardNumber(String cardNumber) throws Exception {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number must not be null or blank");
        }
        return AESUtil.encrypt(cardNumber, aesKey);
    }

    /**
     * Decrypts an encrypted card number retrieved from the database.
     *
     * @param encrypted Base64-encoded "IV:CIPHERTEXT" string from the DB
     * @return the original plain card number
     * @throws Exception if AES decryption fails (e.g. tampered ciphertext)
     */
    public String decryptCardNumber(String encrypted) throws Exception {
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalArgumentException("Encrypted card number must not be null or blank");
        }
        return AESUtil.decrypt(encrypted, aesKey);
    }

    // ── CVV ───────────────────────────────────────────────────────────────────

    /**
     * Encrypts a CVV for secure storage.
     *
     * Note: PCI-DSS prohibits storing CVVs after authorisation in most contexts.
     * Use this only if your architecture requires transient encrypted storage
     * during the authorisation window, and purge it immediately after.
     *
     * @param cvv the plain 3-digit CVV string
     * @return Base64-encoded "IV:CIPHERTEXT" string
     * @throws Exception if AES encryption fails
     */
    public String encryptCVV(String cvv) throws Exception {
        if (cvv == null || cvv.isBlank()) {
            throw new IllegalArgumentException("CVV must not be null or blank");
        }
        return AESUtil.encrypt(cvv, aesKey);
    }

    /**
     * Decrypts an encrypted CVV.
     *
     * @param encrypted Base64-encoded "IV:CIPHERTEXT" string
     * @return the original plain CVV
     * @throws Exception if AES decryption fails
     */
    public String decryptCVV(String encrypted) throws Exception {
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalArgumentException("Encrypted CVV must not be null or blank");
        }
        return AESUtil.decrypt(encrypted, aesKey);
    }

    // ── Display masking (no encryption) ──────────────────────────────────────

    /**
     * Returns a masked representation of the card number for UI display.
     * Format: {@code ****-****-****-XXXX} where XXXX is the last 4 digits.
     *
     * This method does NOT encrypt — it is for safe display only.
     *
     * @param cardNumber the plain or unmasked card number (must be at least 4 chars)
     * @return masked string, e.g. {@code ****-****-****-4242}
     * @throws IllegalArgumentException if cardNumber is null or shorter than 4 digits
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            throw new IllegalArgumentException("Card number must be at least 4 characters to mask");
        }
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        return "****-****-****-" + last4;
    }
}
