package com.votingbooth.vote.job.service;

import com.votingbooth.vote.job.dataaccess.repository.ParliamentVoteJdbcRepository;
import com.votingbooth.vote.job.model.sync.ParsedScrutin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ScrutinSyncService {

    private final ParliamentVoteJdbcRepository parliamentVoteRepository;

    public ScrutinSyncService(ParliamentVoteJdbcRepository parliamentVoteRepository) {
        this.parliamentVoteRepository = parliamentVoteRepository;
    }

    // One transaction per scrutin: its hash is only recorded with its rows, so a failure leaves the old version whole and
    // the file is retried on the next run.
    @Transactional
    public void sync(String source, ParsedScrutin scrutin, String s3Key, String s3VersionId, String sha256, LocalDateTime now) {
        UUID voteId = parliamentVoteRepository.upsert(source, scrutin, s3Key, s3VersionId, sha256, now);
        parliamentVoteRepository.replaceRows(voteId, scrutin);
    }

}
