package com.smartrecruit.application.repository;

import com.smartrecruit.application.model.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Team notes on applications (EXT-APP-03). */
public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {

    List<ApplicationNote> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    /** Rows of {@code [applicationId, count, average rating|null]}; SQL {@code avg} ignores unrated notes. */
    @Query("select n.applicationId, count(n), avg(n.rating) from ApplicationNote n where n.applicationId in :applicationIds group by n.applicationId")
    List<Object[]> summarize(Collection<UUID> applicationIds);

    @Modifying
    @Query("delete from ApplicationNote n where n.applicationId in :applicationIds")
    int deleteByApplicationIds(Collection<UUID> applicationIds);
}
