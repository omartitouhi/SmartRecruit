package com.smartrecruit.user.storage;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption of CV files at rest (BE-SEC-01). GCM also authenticates the content,
 * so any alteration of a stored file is detected on decryption.
 * <p>
 * Stored format: {@code [version:1][iv:12][ciphertext + tag:16]}. The version byte leaves room for
 * a future algorithm or key rotation.
 */
public class CvCipher {

    static final byte FORMAT_VERSION = 1;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param base64Key 32 random bytes encoded in base64 (e.g. {@code openssl rand -base64 32})
     */
    public CvCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CV_ENCRYPTION_KEY n'est pas du base64 valide", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("CV_ENCRYPTION_KEY doit contenir exactement 32 octets (AES-256), reçu "
                    + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + IV_BYTES + ciphertext.length)
                    .put(FORMAT_VERSION).put(iv).put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement du CV impossible", e);
        }
    }

    /**
     * @throws CorruptedCvException if the data was altered, encrypted with another key or has an
     *                              unknown format
     */
    public byte[] decrypt(byte[] stored) {
        if (stored.length < 1 + IV_BYTES + TAG_BITS / 8 || stored[0] != FORMAT_VERSION) {
            throw new CorruptedCvException();
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
            return cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new CorruptedCvException();
        }
    }
}
