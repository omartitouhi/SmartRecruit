package com.smartrecruit.user.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("another-32-bytes-long-secret-key".getBytes());
    private static final byte[] PDF = "%PDF-1.7 Nom: Alice Martin, tel 20123456".getBytes(StandardCharsets.UTF_8);

    private final CvCipher cipher = new CvCipher(KEY);

    @Test
    void roundTripRestoresTheOriginalContent() {
        assertThat(cipher.decrypt(cipher.encrypt(PDF))).isEqualTo(PDF);
    }

    @Test
    void storedBytesDoNotContainThePlaintext() {
        byte[] encrypted = cipher.encrypt(PDF);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF").doesNotContain("Alice");
        assertThat(encrypted[0]).isEqualTo(CvCipher.FORMAT_VERSION);
        // version + IV + ciphertext + GCM tag
        assertThat(encrypted).hasSize(1 + 12 + PDF.length + 16);
    }

    @Test
    void sameContentEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt(PDF)).isNotEqualTo(cipher.encrypt(PDF));
    }

    @Test
    void alteredContentIsDetected() {
        byte[] encrypted = cipher.encrypt(PDF);
        encrypted[encrypted.length - 20] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(CorruptedCvException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] encrypted = cipher.encrypt(PDF);

        assertThatThrownBy(() -> new CvCipher(OTHER_KEY).decrypt(encrypted)).isInstanceOf(CorruptedCvException.class);
    }

    @Test
    void plaintextOrTruncatedDataIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt(PDF)).isInstanceOf(CorruptedCvException.class);
        assertThatThrownBy(() -> cipher.decrypt(new byte[5])).isInstanceOf(CorruptedCvException.class);
    }

    @Test
    void invalidKeysAreRefusedAtStartup() {
        assertThatThrownBy(() -> new CvCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CvCipher("not base64 !!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CvCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 octets");
    }
}
