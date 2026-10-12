package com.smartrecruit.user.config;

import com.smartrecruit.user.storage.CvCipher;
import com.smartrecruit.user.storage.CvStorage;
import com.smartrecruit.user.storage.EncryptingCvStorage;
import com.smartrecruit.user.storage.FileSystemCvStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class CvStorageConfig {

    /** CVs are always encrypted at rest (BE-SEC-01); the service fails to start without a valid key. */
    @Bean
    public CvStorage cvStorage(@Value("${cv-storage.directory}") Path directory,
                               @Value("${cv-storage.encryption-key}") String encryptionKey) {
        return new EncryptingCvStorage(new FileSystemCvStorage(directory), new CvCipher(encryptionKey));
    }
}
