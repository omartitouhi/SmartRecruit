package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.smartrecruit.user.storage.CvNotFoundException;
import com.smartrecruit.user.storage.CvStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static com.smartrecruit.user.controller.CandidateProfileCreateIntegrationTest.FULL_PROFILE;
import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-USER-02 — POST /api/v1/users/candidates/me/cv and download through cvUrl.
 * BE-SEC-01 — CVs are encrypted at rest and only decrypted for an authorized download.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateCvIntegrationTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository repository;

    @Autowired
    private CvStorage cvStorage;

    @Value("${cv-storage.directory}")
    private String storageDirectory;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        mockMvc.perform(put("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isCreated());
    }

    private ResultActions upload(UUID userId, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/users/candidates/me/cv").file(file)
                .with(as(verifier, userId, "CANDIDATE")));
    }

    private static MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, content);
    }

    @Test
    void validPdfIsStoredAndLinkedToTheProfile() throws Exception {
        upload(candidateId, pdf(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvUrl").value("/api/v1/users/candidates/" + candidateId + "/cv"))
                .andExpect(jsonPath("$.cvStorageKey").doesNotExist())
                .andExpect(jsonPath("$.cvParsedAt").doesNotExist());

        CandidateProfile profile = repository.findById(candidateId).orElseThrow();
        assertThat(profile.getCvStorageKey()).isNotBlank();
        assertThat(cvStorage.load(profile.getCvStorageKey())).isEqualTo(PDF);
    }

    @Test
    void cvIsEncryptedOnDiskAndDecryptedOnAuthorizedDownload() throws Exception {
        upload(candidateId, pdf(PDF)).andExpect(status().isOk());
        String key = repository.findById(candidateId).orElseThrow().getCvStorageKey();

        byte[] onDisk = Files.readAllBytes(Path.of(storageDirectory, key + ".cv"));
        assertThat(onDisk).isNotEqualTo(PDF);
        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");

        mockMvc.perform(get("/api/v1/users/candidates/{id}/cv", candidateId).with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
    }

    @Test
    void tamperedFileIsNotServed() throws Exception {
        upload(candidateId, pdf(PDF)).andExpect(status().isOk());
        String key = repository.findById(candidateId).orElseThrow().getCvStorageKey();
        Path file = Path.of(storageDirectory, key + ".cv");
        byte[] onDisk = Files.readAllBytes(file);
        onDisk[onDisk.length - 1] ^= 0x01;
        Files.write(file, onDisk);

        mockMvc.perform(get("/api/v1/users/candidates/{id}/cv", candidateId).with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Le CV stocké est illisible"));
    }

    @Test
    void ownerCanDownloadTheCvThroughCvUrl() throws Exception {
        upload(candidateId, pdf(PDF)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/candidates/{id}/cv", candidateId).with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void recruitersAndAdminsCanDownloadButNotOtherCandidates() throws Exception {
        upload(candidateId, pdf(PDF)).andExpect(status().isOk());
        String url = "/api/v1/users/candidates/" + candidateId + "/cv";

        mockMvc.perform(get(url).with(as(verifier, UUID.randomUUID(), "RECRUITER"))).andExpect(status().isOk());
        mockMvc.perform(get(url).with(as(verifier, UUID.randomUUID(), "ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get(url).with(as(verifier, UUID.randomUUID(), "CANDIDATE"))).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void newUploadReplacesAndDeletesThePreviousFile() throws Exception {
        upload(candidateId, pdf(PDF)).andExpect(status().isOk());
        CandidateProfile first = repository.findById(candidateId).orElseThrow();
        first.setCvParsedAt(Instant.now());
        repository.save(first);
        String firstKey = first.getCvStorageKey();
        byte[] secondPdf = "%PDF-1.4 second version".getBytes(StandardCharsets.US_ASCII);

        upload(candidateId, pdf(secondPdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvParsedAt").doesNotExist());

        CandidateProfile second = repository.findById(candidateId).orElseThrow();
        assertThat(second.getCvStorageKey()).isNotEqualTo(firstKey);
        assertThat(cvStorage.load(second.getCvStorageKey())).isEqualTo(secondPdf);
        assertThatThrownBy(() -> cvStorage.load(firstKey)).isInstanceOf(CvNotFoundException.class);
    }

    @Test
    void nonPdfFileIsRejected() throws Exception {
        upload(candidateId, new MockMultipartFile("file", "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PK\u0003\u0004".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Format non supporté : le CV doit être un fichier PDF"));

        assertThat(repository.findById(candidateId).orElseThrow().getCvUrl()).isNull();
    }

    @Test
    void fileClaimingToBePdfButWithOtherContentIsRejected() throws Exception {
        upload(candidateId, pdf("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileAbove5MbIsRejected() throws Exception {
        byte[] big = Arrays.copyOf(PDF, 5 * 1024 * 1024 + 1);

        upload(candidateId, pdf(big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le CV dépasse la taille maximale de 5 Mo"));
    }

    @Test
    void fileOfExactly5MbIsAccepted() throws Exception {
        upload(candidateId, pdf(Arrays.copyOf(PDF, 5 * 1024 * 1024))).andExpect(status().isOk());
    }

    @Test
    void missingOrEmptyFileIsRejected() throws Exception {
        upload(candidateId, pdf(new byte[0])).andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/users/candidates/me/cv").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadWithoutProfileReturns404() throws Exception {
        upload(UUID.randomUUID(), pdf(PDF)).andExpect(status().isNotFound());
    }

    @Test
    void downloadWithoutCvReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/{id}/cv", candidateId).with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyCandidatesCanUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/users/candidates/me/cv").file(pdf(PDF))
                        .with(as(verifier, candidateId, "RECRUITER")))
                .andExpect(status().isForbidden());
    }
}
