package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.storage.CorruptedCvException;
import com.smartrecruit.user.storage.CvNotFoundException;
import com.smartrecruit.user.storage.CvStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * CV upload and download (BE-USER-02). Files are encrypted at rest by the {@link CvStorage}
 * bean (BE-SEC-01) and only decrypted here, for an authorized download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateCvService {

    public static final long MAX_CV_SIZE = 5L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final CandidateProfileRepository profileRepository;
    private final CvStorage cvStorage;

    /** Public reference stored in {@code cvUrl}: the download endpoint, never the storage location. */
    public static String cvUrlOf(UUID candidateId) {
        return "/api/v1/users/candidates/" + candidateId + "/cv";
    }

    /**
     * Stores the PDF and links it to the profile. A previous CV is deleted once the new one is
     * committed, and {@code cvParsedAt} is reset since the new file has not been analysed yet.
     */
    @Transactional
    public CandidateProfileResponse upload(UUID candidateId, MultipartFile file) {
        byte[] content = validatePdf(file);
        CandidateProfile profile = profileRepository.findById(candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Profil candidat introuvable : créez-le avant d'envoyer un CV"));

        String previousKey = profile.getCvStorageKey();
        String newKey = cvStorage.store(content);
        afterTransaction(previousKey, newKey);

        profile.setCvStorageKey(newKey);
        profile.setCvUrl(cvUrlOf(candidateId));
        profile.setCvParsedAt(null);
        return CandidateProfileResponse.from(profileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public byte[] download(UUID candidateId) {
        CandidateProfile profile = profileRepository.findById(candidateId)
                .filter(found -> found.getCvStorageKey() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Aucun CV pour ce candidat"));
        try {
            return cvStorage.load(profile.getCvStorageKey());
        } catch (CvNotFoundException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Aucun CV pour ce candidat");
        } catch (CorruptedCvException e) {
            log.error("CV du candidat {} indéchiffrable (fichier altéré ou mauvaise clé)", candidateId);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Le CV stocké est illisible");
        }
    }

    private static byte[] validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucun fichier reçu : envoyez le CV dans le champ 'file'");
        }
        if (file.getSize() > MAX_CV_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le CV dépasse la taille maximale de 5 Mo");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible", e);
        }
        boolean declaredPdf = "application/pdf".equalsIgnoreCase(file.getContentType());
        boolean looksLikePdf = content.length >= PDF_SIGNATURE.length
                && Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);
        if (!declaredPdf || !looksLikePdf) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Format non supporté : le CV doit être un fichier PDF");
        }
        return content;
    }

    /** Keeps storage consistent with the database whatever the transaction outcome. */
    private void afterTransaction(String previousKey, String newKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && previousKey != null) {
                    cvStorage.delete(previousKey);
                } else if (status != STATUS_COMMITTED) {
                    cvStorage.delete(newKey);
                }
            }
        });
    }
}
