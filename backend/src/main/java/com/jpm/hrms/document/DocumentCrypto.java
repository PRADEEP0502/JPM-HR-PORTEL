package com.jpm.hrms.document;

import com.jpm.hrms.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for documents at rest. File layout: 12-byte random IV followed by
 * ciphertext + 16-byte tag. The key comes from DOCUMENT_ENCRYPTION_KEY (base64, 32 bytes).
 */
@Component
public class DocumentCrypto {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public DocumentCrypto(AppProperties props) {
        String b64 = props.documentEncryptionKey();
        if (!StringUtils.hasText(b64)) {
            key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DOCUMENT_ENCRYPTION_KEY must be base64 encoded");
        }
        if (raw.length != 32) throw new IllegalStateException("DOCUMENT_ENCRYPTION_KEY must decode to exactly 32 bytes");
        key = new SecretKeySpec(raw, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = Arrays.copyOf(iv, IV_BYTES + ct.length);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored.length < IV_BYTES + TAG_BITS / 8) throw new IllegalStateException("Encrypted file is truncated");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            return c.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed - wrong DOCUMENT_ENCRYPTION_KEY or corrupted file", e);
        }
    }

    private SecretKey requireKey() {
        if (key == null) throw new IllegalStateException("DOCUMENT_ENCRYPTION_KEY is not configured");
        return key;
    }
}
