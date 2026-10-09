package com.smartrecruit.user.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores each CV as a file named after a random UUID in {@code cv-storage.directory}.
 * Keys are validated as UUIDs so they can never be used for path traversal.
 */
@Component
public class FileSystemCvStorage implements CvStorage {

    private final Path directory;

    public FileSystemCvStorage(@Value("${cv-storage.directory}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.createDirectories(directory);
            Files.write(pathOf(key), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'enregistrer le CV", e);
        }
        return key;
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(pathOf(key));
        } catch (NoSuchFileException e) {
            throw new CvNotFoundException(key);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le CV", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(pathOf(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de supprimer le CV", e);
        }
    }

    private Path pathOf(String key) {
        return directory.resolve(UUID.fromString(key) + ".cv");
    }
}
