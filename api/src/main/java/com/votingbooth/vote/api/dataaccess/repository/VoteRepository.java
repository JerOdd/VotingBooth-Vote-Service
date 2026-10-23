package com.votingbooth.vote.api.dataaccess.repository;

import com.votingbooth.vote.api.model.entity.Vote;
import com.votingbooth.vote.api.model.entity.VoteId;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, VoteId> {

    // Plain INSERT (not save()) so a duplicate vote fails on the primary key instead of being merged.
    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO votes (user_id, law_id, value, created_at) VALUES (:userId, :lawId, :value, NOW())",
            nativeQuery = true
    )
    void create(
            @Param("userId") UUID userId,
            @Param("lawId") UUID lawId,
            @Param("value") Integer value
    );

    @Query(
            value = """
                    SELECT COUNT(*) FILTER (WHERE value = 1)          AS yes,
                           COUNT(*) FILTER (WHERE value = 0)          AS no,
                           COUNT(*) FILTER (WHERE value NOT IN (0, 1)) AS abstain
                    FROM votes
                    WHERE law_id = :lawId
                    """,
            nativeQuery = true
    )
    VoteCounts countByLawId(@Param("lawId") UUID lawId);

    interface VoteCounts {
        long getYes();
        long getNo();
        long getAbstain();
    }

}
