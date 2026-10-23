package com.votingbooth.vote.core.dataaccess.repository;

import com.votingbooth.vote.core.model.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    // The newest archive version already applied: an older download must not overwrite it.
    @Query("""
            SELECT MAX(run.archiveLastModified) FROM SyncRun run
            WHERE run.source = :source AND run.archiveUrl = :archiveUrl
              AND run.status = com.votingbooth.vote.core.model.entity.SyncStatus.SUCCEEDED
            """)
    Optional<LocalDateTime> findLatestAppliedArchive(@Param("source") String source, @Param("archiveUrl") String archiveUrl);

}
