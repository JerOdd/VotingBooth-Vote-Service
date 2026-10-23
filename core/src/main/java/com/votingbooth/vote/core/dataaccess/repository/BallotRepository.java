package com.votingbooth.vote.core.dataaccess.repository;

import com.votingbooth.vote.core.model.entity.Ballot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface BallotRepository extends JpaRepository<Ballot, UUID> {

    // One open ballot per censure motion, created once: an existing ballot is left as it is (its status may have changed).
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO ballot.ballot (id, kind, source, external_id, title, opens_at, status, created_at, updated_at)
                    SELECT gen_random_uuid(), 'CENSURE_MOTION', vote.source, vote.external_id, vote.title, :now, 'OPEN', :now, :now
                    FROM parliament.parliament_vote vote
                    WHERE vote.source = :source AND vote.vote_type = 'MOC'
                    ON CONFLICT (kind, source, external_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int createCensureMotionBallots(@Param("source") String source, @Param("now") LocalDateTime now);

    // The motion's own scrutin is the ballot's decisive vote: voting for the motion is voting for the censure.
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO ballot.ballot_parliament_vote (ballot_id, parliament_vote_id, is_decisive, alignment)
                    SELECT ballot.id, vote.id, TRUE, 'SAME'
                    FROM ballot.ballot ballot
                    JOIN parliament.parliament_vote vote
                      ON vote.source = ballot.source AND vote.external_id = ballot.external_id
                    WHERE ballot.kind = 'CENSURE_MOTION' AND ballot.source = :source
                    ON CONFLICT (ballot_id, parliament_vote_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int linkCensureMotionBallots(@Param("source") String source);

}
