package com.example.demo.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM authenticated encryption for server-side data-at-rest.
 *
 * <h2>Use cases</h2>
 * <ul>
 *   <li>Encrypting PII fields (phone, email) before persisting.</li>
 *   <li>Encrypting message payloads when E2EE is OFF (server-side encryption fallback).</li>
 *   <li>Wrapping cached responses, tokens, push tokens.</li>
 * </ul>
 *
 * <h2>What this is NOT for</h2>
 * <p>For end-to-end encrypted messages between two users the server NEVER holds the key:
 * the client encrypts with the recipient's public key and the server only stores opaque
 * ciphertext. See {@link com.example.demo.entity.UserIdentityKey} for the E2EE flow.</p>
 *
 * <h2>Wire format</h2>
 * Output of {@link #encrypt(byte[])} is base64(IV ‖ ciphertext ‖ 16-byte GCM tag).
 * The IV is 12 random bytes generated per encryption (NIST SP 800-38D recommendation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    public static final String ALGO = "AES";
    public static final String TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int IV_LENGTH = 12;          // 96 bits
    public static final int TAG_LENGTH_BITS = 128;    // 128-bit auth tag
    public static final int KEY_LENGTH_BYTES = 32;    // AES-256

    private final SecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey serverKey;

    @PostConstruct
    void init() {
        String b64 = properties.getEncryptionKeyBase64();
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException(
                    "app.security.encryption-key-base64 is missing — refusing to start.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.security.encryption-key-base64 is not valid base64.", e);
        }
        if (raw.length < KEY_LENGTH_BYTES) {
            // Stretch / pad short keys to 256-bit by hashing — protects against
            // misconfiguration but logs a strong warning.
            log.warn("Encryption key is {} bytes; expected {}. Deriving 256-bit key via SHA-256.",
                    raw.length, KEY_LENGTH_BYTES);
            raw = sha256(raw);
        } else if (raw.length > KEY_LENGTH_BYTES) {
            byte[] trimmed = new byte[KEY_LENGTH_BYTES];
            System.arraycopy(raw, 0, trimmed, 0, KEY_LENGTH_BYTES);
            raw = trimmed;
        }
        this.serverKey = new SecretKeySpec(raw, ALGO);
        log.info("EncryptionService ready — AES-{}, GCM mode.", KEY_LENGTH_BYTES * 8);
    }

    // ------------------------------------------------------------------
    //  String helpers
    // ------------------------------------------------------------------

    /** Encrypts a UTF-8 string. Returns base64(iv ‖ ciphertext ‖ tag). */
    public String encryptString(String plain) {
        if (plain == null) return null;
        byte[] cipher = encrypt(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipher);
    }

    /** Decrypts a payload produced by {@link #encryptString(String)}. */
    public String decryptString(String base64) {
        if (base64 == null) return null;
        byte[] decoded = Base64.getDecoder().decode(base64);
        byte[] plain = decrypt(decoded);
        return new String(plain, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    //  Raw byte helpers
    // ------------------------------------------------------------------

    /** Encrypts bytes; returns iv ‖ ciphertext ‖ tag. */
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, serverKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);

            byte[] out = new byte[IV_LENGTH + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ct, 0, out, IV_LENGTH, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /** Inverse of {@link #encrypt(byte[])}. Throws if auth tag fails. */
    public byte[] decrypt(byte[] ivPlusCipher) {
        if (ivPlusCipher == null) return null;
        if (ivPlusCipher.length < IV_LENGTH + 16) {
            throw new IllegalArgumentException("Ciphertext too short.");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[ivPlusCipher.length - IV_LENGTH];
            System.arraycopy(ivPlusCipher, 0, iv, 0, IV_LENGTH);
            System.arraycopy(ivPlusCipher, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, serverKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    /** Convenience: derive a new random AES-256 key (caller's responsibility to store safely). */
    public byte[] generateNewKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        secureRandom.nextBytes(key);
        return key;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
