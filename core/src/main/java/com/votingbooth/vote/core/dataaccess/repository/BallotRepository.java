package com.votingbooth.vote.core.dataaccess.repository;

import com.votingbooth.vote.core.model.entity.Ballot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
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

    // One open ballot per law, created once.
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO ballot.ballot (id, kind, law_id, opens_at, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), 'LAW_FINAL_VOTE', :lawId, :now, 'OPEN', :now, :now)
                    ON CONFLICT (law_id) WHERE kind = 'LAW_FINAL_VOTE' DO NOTHING
                    """,
            nativeQuery = true
    )
    int createLawBallot(@Param("lawId") UUID lawId, @Param("now") LocalDateTime now);

    // Law ballots' links are rebuilt from the law service's on every run: a new reading can change the decisive scrutin.
    @Modifying
    @Transactional
    @Query(
            value = """
                    DELETE FROM ballot.ballot_parliament_vote link
                    USING ballot.ballot ballot
                    WHERE ballot.id = link.ballot_id AND ballot.kind = 'LAW_FINAL_VOTE'
                    """,
            nativeQuery = true
    )
    void deleteLawBallotLinks();

    // Links the law's ballot to a scrutin, if the scrutin is stored (not for Senate or L15 scrutins). Returns 0 otherwise.
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO ballot.ballot_parliament_vote (ballot_id, parliament_vote_id, is_decisive, alignment)
                    SELECT ballot.id, vote.id, :decisive, :alignment
                    FROM ballot.ballot ballot
                    JOIN parliament.parliament_vote vote ON vote.source = :source AND vote.external_id = :scrutinExternalId
                    WHERE ballot.kind = 'LAW_FINAL_VOTE' AND ballot.law_id = :lawId
                    ON CONFLICT (ballot_id, parliament_vote_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int linkLawBallot(@Param("lawId") UUID lawId, @Param("source") String source,
                      @Param("scrutinExternalId") String scrutinExternalId, @Param("decisive") boolean decisive,
                      @Param("alignment") String alignment);

    // For a law passed by 49.3, which has no vote on the whole text: its last censure motion decides.
    @Modifying
    @Transactional
    @Query(
            value = """
                    UPDATE ballot.ballot_parliament_vote link SET is_decisive = TRUE
                    FROM (SELECT motion.ballot_id, motion.parliament_vote_id
                          FROM ballot.ballot_parliament_vote motion
                          JOIN ballot.ballot ballot ON ballot.id = motion.ballot_id
                          JOIN parliament.parliament_vote vote ON vote.id = motion.parliament_vote_id
                          WHERE ballot.kind = 'LAW_FINAL_VOTE' AND ballot.law_id = :lawId AND motion.alignment = 'OPPOSITE'
                          ORDER BY vote.vote_date DESC, vote.number DESC
                          LIMIT 1) latest
                    WHERE link.ballot_id = latest.ballot_id AND link.parliament_vote_id = latest.parliament_vote_id
                    """,
            nativeQuery = true
    )
    int markLatestMotionDecisive(@Param("lawId") UUID lawId);

    // The scrutin that decided a law, if the law has a ballot with a decisive scrutin.
    @Query(
            value = """
                    SELECT vote.vote_date AS voteDate, vote.result AS result, link.alignment AS alignment,
                           vote.count_for AS countFor, vote.count_against AS countAgainst,
                           vote.count_abstain AS countAbstain, vote.count_non_voting AS countNonVoting
                    FROM ballot.ballot ballot
                    JOIN ballot.ballot_parliament_vote link ON link.ballot_id = ballot.id AND link.is_decisive
                    JOIN parliament.parliament_vote vote ON vote.id = link.parliament_vote_id
                    WHERE ballot.kind = 'LAW_FINAL_VOTE' AND ballot.law_id = :lawId
                    """,
            nativeQuery = true
    )
    Optional<DecisiveVote> findDecisiveVote(@Param("lawId") UUID lawId);

    interface DecisiveVote {
        LocalDate getVoteDate();
        String getResult();
        String getAlignment();
        int getCountFor();
        int getCountAgainst();
        int getCountAbstain();
        int getCountNonVoting();
    }

}
