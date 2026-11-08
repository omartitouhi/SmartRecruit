package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.dto.AuditEventRequest;
import com.smartrecruit.dashboard.dto.AuditLogEntryResponse;
import com.smartrecruit.dashboard.dto.PageResponse;
import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** Who did what and when on sensitive data (BE-ADMIN-03). */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogEntry record(AuditEventRequest event) {
        return repository.save(AuditLogEntry.builder()
                .actorId(event.actorId())
                .action(event.action())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .timestamp(clock.instant())
                .build());
    }

    /** Newest first; every filter is optional and {@code from}/{@code to} are inclusive. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogEntryResponse> search(Instant from, Instant to, UUID actorId, String action,
                                                      int page, int size) {
        Specification<AuditLogEntry> filters = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("timestamp").descending().and(Sort.by("id")));
        return PageResponse.of(repository.findAll(filters, pageable).map(AuditLogEntryResponse::from));
    }
}
