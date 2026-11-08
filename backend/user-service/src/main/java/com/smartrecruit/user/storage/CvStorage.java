package com.smartrecruit.user.storage;

/**
 * Storage of CV files. Callers only handle opaque keys, never physical paths or URLs
 * ({@code DATA_MODEL.md} §3.2: {@code cvUrl} is never the raw storage location).
 */
public interface CvStorage {

    /** @return the key under which the content was stored */
    String store(byte[] content);

    /** @throws CvNotFoundException if nothing is stored under this key */
    byte[] load(String key);

    /** Removes the file; unknown keys are ignored. */
    void delete(String key);
}
