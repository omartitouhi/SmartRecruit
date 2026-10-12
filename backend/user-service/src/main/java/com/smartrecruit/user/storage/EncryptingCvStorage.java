package com.smartrecruit.user.storage;

/**
 * Decorator that encrypts CVs before they reach the underlying storage and decrypts them on
 * read (BE-SEC-01). Plain content never touches the disk.
 */
public class EncryptingCvStorage implements CvStorage {

    private final CvStorage delegate;
    private final CvCipher cipher;

    public EncryptingCvStorage(CvStorage delegate, CvCipher cipher) {
        this.delegate = delegate;
        this.cipher = cipher;
    }

    @Override
    public String store(byte[] content) {
        return delegate.store(cipher.encrypt(content));
    }

    @Override
    public byte[] load(String key) {
        return cipher.decrypt(delegate.load(key));
    }

    @Override
    public void delete(String key) {
        delegate.delete(key);
    }
}
