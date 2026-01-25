package ar.com.personalfinances.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;        // recomendado GCM
    private static final int TAG_SIZE = 128;      // bits

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SecretKey KEY = loadKey();

    private CryptoService() {}

    // =========================
    // Public API
    // =========================

    public static String encrypt(String plainText) {
        if (plainText == null) return null;

        try {
            byte[] iv = new byte[IV_SIZE];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    KEY,
                    new GCMParameterSpec(TAG_SIZE, iv)
            );

            byte[] cipherText = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            // IV + cipherText juntos
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new IllegalStateException("Error encrypting data", e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null) return null;

        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_SIZE];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    KEY,
                    new GCMParameterSpec(TAG_SIZE, iv)
            );

            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("Error decrypting data", e);
        }
    }

    // =========================
    // Key loading
    // =========================

    private static SecretKey loadKey() {
        String base64Key = System.getenv("APP_CRYPTO_KEY");

        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("APP_CRYPTO_KEY env var not set");
        }

        byte[] decoded = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decoded, "AES");
    }
}