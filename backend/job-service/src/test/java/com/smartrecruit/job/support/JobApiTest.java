package com.smartrecruit.job.support;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base of the job API integration tests: MockMvc over the real application on H2, with the other
 * services replaced by {@link FakeBackends}. Requests carry the identity the api-gateway would sign.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class JobApiTest {

    protected static final FakeBackends BACKENDS = FakeBackends.INSTANCE;

    @DynamicPropertySource
    static void pointToFakeBackends(DynamicPropertyRegistry registry) {
        registry.add("services.ai-service-uri", BACKENDS::uri);
        registry.add("services.user-service-uri", BACKENDS::uri);
        registry.add("services.application-service-uri", BACKENDS::uri);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected InternalIdentityVerifier verifier;

    @Autowired
    protected JobRepository jobRepository;

    @BeforeEach
    void resetBackendsAndData() {
        BACKENDS.reset();
        jobRepository.deleteAll();
    }

    protected RequestPostProcessor as(UUID accountId, String role) {
        return request -> {
            long now = Instant.now().getEpochSecond();
            request.addHeader(InternalIdentityVerifier.USER_ID, accountId.toString());
            request.addHeader(InternalIdentityVerifier.USER_ROLE, role);
            request.addHeader(InternalIdentityVerifier.TIMESTAMP, Long.toString(now));
            request.addHeader(InternalIdentityVerifier.SIGNATURE, verifier.sign(accountId.toString(), role, now));
            return request;
        };
    }

    /** user-service knows {@code accountId} as a member of {@code companyId}. */
    protected void memberOf(UUID accountId, UUID companyId, String companyName) {
        BACKENDS.respond("/internal/v1/companies/by-member/" + accountId, 200,
                "{\"id\": \"%s\", \"name\": \"%s\", \"logoUrl\": null}".formatted(companyId, companyName));
    }

    /** user-service directory answering the batch lookup used for {@code companyName}. */
    protected void directoryKnows(UUID companyId, String companyName) {
        BACKENDS.respond("/internal/v1/companies", 200,
                "[{\"id\": \"%s\", \"name\": \"%s\", \"logoUrl\": null}]".formatted(companyId, companyName));
    }

    protected Job saveJob(UUID companyId, String title, JobStatus status) {
        Instant now = Instant.parse("2026-10-01T09:00:00Z");
        return jobRepository.save(Job.builder()
                .id(UUID.randomUUID()).companyId(companyId).title(title)
                .description("Une description suffisamment longue pour le poste")
                .requiredSkills(new ArrayList<>(List.of("Java", "Spring")))
                .experienceLevel(ExperienceLevel.SENIOR).contractType(ContractType.CDI)
                .location("Tunis").status(status)
                .publishedAt(status == JobStatus.DRAFT ? null : now).updatedAt(now)
                .build());
    }
}
