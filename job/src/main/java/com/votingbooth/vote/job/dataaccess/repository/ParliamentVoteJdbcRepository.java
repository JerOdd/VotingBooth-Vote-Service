package com.votingbooth.vote.job.dataaccess.repository;

import com.votingbooth.vote.job.model.sync.ParsedScrutin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The job's writes to the parliament schema, in plain JDBC rather than JPA: a legislature is about 1.3 million
// representative_vote rows, inserted in batches (the datasource sets reWriteBatchedInserts).
// Callers hold the transaction: a scrutin and its rows are replaced together.
@Repository
public class ParliamentVoteJdbcRepository {

    private static final String JURISDICTION = "FR";
    private static final String CHAMBER = "AN";

    private final JdbcTemplate jdbcTemplate;

    public ParliamentVoteJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // external_id -> raw_sha256 of the scrutins already stored for a legislature.
    public Map<String, String> findHashes(String source, int legislature) {
        Map<String, String> hashes = new HashMap<>();
        jdbcTemplate.query("SELECT external_id, raw_sha256 FROM parliament.parliament_vote WHERE source = ? AND legislature = ?",
                rs -> {
                    hashes.put(rs.getString(1), rs.getString(2));
                }, source, legislature);
        return hashes;
    }

    // Keeps the row's id, so ballots linked to the scrutin stay linked. A rerun of unchanged content (--force) puts nothing
    // in S3 and passes no version id: the stored one is kept.
    public UUID upsert(String source, ParsedScrutin scrutin, String s3Key, String s3VersionId, String sha256, LocalDateTime now) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO parliament.parliament_vote (id, source, external_id, jurisdiction_code, chamber_code, legislature,
                            number, vote_date, session_ref, sitting_ref, vote_type, result, title, normalized_title, kind, requester,
                            dossier_external_id, voters, expressed, required_majority, count_for, count_against, count_abstain,
                            count_non_voting, raw_s3_key, raw_s3_version_id, raw_sha256, first_seen_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (source, external_id) DO UPDATE SET
                            legislature = EXCLUDED.legislature, number = EXCLUDED.number, vote_date = EXCLUDED.vote_date,
                            session_ref = EXCLUDED.session_ref, sitting_ref = EXCLUDED.sitting_ref, vote_type = EXCLUDED.vote_type,
                            result = EXCLUDED.result, title = EXCLUDED.title, normalized_title = EXCLUDED.normalized_title,
                            kind = EXCLUDED.kind, requester = EXCLUDED.requester, dossier_external_id = EXCLUDED.dossier_external_id,
                            voters = EXCLUDED.voters, expressed = EXCLUDED.expressed, required_majority = EXCLUDED.required_majority,
                            count_for = EXCLUDED.count_for, count_against = EXCLUDED.count_against,
                            count_abstain = EXCLUDED.count_abstain, count_non_voting = EXCLUDED.count_non_voting,
                            raw_s3_key = EXCLUDED.raw_s3_key,
                            raw_s3_version_id = COALESCE(EXCLUDED.raw_s3_version_id, parliament_vote.raw_s3_version_id),
                            raw_sha256 = EXCLUDED.raw_sha256, updated_at = EXCLUDED.updated_at
                        RETURNING id
                        """, UUID.class,
                UUID.randomUUID(), source, scrutin.externalId(), JURISDICTION, CHAMBER, scrutin.legislature(), scrutin.number(),
                Date.valueOf(scrutin.date()), scrutin.sessionRef(), scrutin.sittingRef(), scrutin.voteType().name(),
                scrutin.result().name(), scrutin.title(), scrutin.normalizedTitle(), scrutin.kind().name(), scrutin.requester(),
                scrutin.dossierExternalId(), scrutin.voters(), scrutin.expressed(), scrutin.requiredMajority(), scrutin.countFor(),
                scrutin.countAgainst(), scrutin.countAbstain(), scrutin.countNonVoting(), s3Key, s3VersionId, sha256,
                Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    // Full replacement: a scrutin's groups, votes and corrections are only ever what its latest file says.
    public void replaceRows(UUID voteId, ParsedScrutin scrutin) {
        jdbcTemplate.update("DELETE FROM parliament.group_vote WHERE parliament_vote_id = ?", voteId);
        jdbcTemplate.update("DELETE FROM parliament.representative_vote WHERE parliament_vote_id = ?", voteId);
        jdbcTemplate.update("DELETE FROM parliament.representative_vote_correction WHERE parliament_vote_id = ?", voteId);

        batch("""
                INSERT INTO parliament.group_vote (parliament_vote_id, group_external_id, group_size, majority_position,
                    count_for, count_against, count_abstain, count_non_voting)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, scrutin.groups().stream().map(group -> new Object[]{voteId, group.groupExternalId(), group.groupSize(),
                group.majorityPosition() == null ? null : group.majorityPosition().name(), group.countFor(),
                group.countAgainst(), group.countAbstain(), group.countNonVoting()}).toList(),
                new int[]{Types.OTHER, Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER});

        batch("""
                INSERT INTO parliament.representative_vote (parliament_vote_id, mandate_external_id, person_external_id,
                    group_external_id, position, by_delegation, non_voting_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, scrutin.votes().stream().map(vote -> new Object[]{voteId, vote.mandateExternalId(), vote.personExternalId(),
                vote.groupExternalId(), vote.position().name(), vote.byDelegation(),
                vote.nonVotingReason() == null ? null : vote.nonVotingReason().name()}).toList(),
                new int[]{Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.VARCHAR});

        batch("""
                INSERT INTO parliament.representative_vote_correction (parliament_vote_id, mandate_external_id,
                    person_external_id, declared_position, is_malfunction)
                VALUES (?, ?, ?, ?, ?)
                """, scrutin.corrections().stream().map(correction -> new Object[]{voteId, correction.mandateExternalId(),
                correction.personExternalId(), correction.declaredPosition().name(), correction.malfunction()}).toList(),
                new int[]{Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN});
    }

    // Typed parameters: null values (majority of a censure motion, non-voting reason) need their SQL type.
    private void batch(String sql, List<Object[]> rows, int[] types) {
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, rows, types);
        }
    }

}
