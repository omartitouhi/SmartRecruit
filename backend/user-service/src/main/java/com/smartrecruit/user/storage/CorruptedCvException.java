package com.smartrecruit.user.storage;

/**
 * A stored CV cannot be decrypted: altered file, wrong key or unknown format.
 */
public class CorruptedCvException extends RuntimeException {

    public CorruptedCvException() {
        super("Le CV stocké est illisible ou a été altéré");
    }
}
