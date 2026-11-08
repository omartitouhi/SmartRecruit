package com.smartrecruit.user.storage;

public class CvNotFoundException extends RuntimeException {

    public CvNotFoundException(String key) {
        super("Aucun CV stocké sous la clé " + key);
    }
}
