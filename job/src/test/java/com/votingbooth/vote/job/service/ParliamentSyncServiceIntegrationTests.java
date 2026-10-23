package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import com.votingbooth.vote.core.model.entity.SyncRun;
import com.votingbooth.vote.core.model.entity.SyncTrigger;
import com.votingbooth.vote.job.dataaccess.client.assembleenationale.ScrutinArchiveClient;
import com.votingbooth.vote.job.dataaccess.client.law.LawVoteLinkClient;
import com.votingbooth.vote.job.dataaccess.client.storage.RawStorageClient;
import com.votingbooth.vote.job.model.law.LawVoteLink;
import com.votingbooth.vote.job.model.scrutin.ArchiveDownload;
import com.votingbooth.vote.job.utils.ScrutinFixtures;
import com.votingbooth.vote.job.utils.ScrutinKindClassifier;
import com.votingbooth.vote.job.utils.ScrutinParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// End to end on mini archives of real files: Flyway schema on a real Postgres, raw storage and download stubbed.
// The scheduler is enabled with a disabled cron ("-") so that the application doesn't run a sync on startup.
// Skipped where Docker isn't available (CircleCI's Docker executor).
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "vote.sync.schedule.enabled=true",
        "vote.sync.cron=-",
        "assemblee-nationale.legislatures=17,16",
        "assemblee-nationale.scrutins-archive-url=https://example.org/{legislature}/Scrutins.json.zip",
        "clients.law.base-url=http://localhost:1",
        "storage.endpoint=http://localhost:1",
        "storage.region=us-east-1",
        "storage.bucket=vote-raw",
        "storage.access-key=test",
        "storage.secret-key=test",
        "assemblee-nationale.stale-archive-retry-delay=PT0S"
})
public class ParliamentSyncServiceIntegrationTests {

    private static final String L17_URL = "https://example.org/17/Scrutins.json.zip";
    private static final String L16_URL = "https://example.org/16/Scrutins.json.zip";
    private static final LocalDateTime ARCHIVE = LocalDateTime.of(2026, 10, 23, 4, 25, 18);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @TempDir
    Path tempDir;

    @MockitoBean
    ScrutinArchiveClient archiveClient;

    @MockitoBean
    RawStorageClient rawStorageClient;

    @MockitoBean
    LawVoteLinkClient lawVoteLinkClient;

    @Autowired
    BallotRepository ballotRepository;

    @Autowired
    ParliamentSyncService parliamentSyncService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE ballot.ballot, parliament.parliament_vote, parliament.sync_run CASCADE");
        when(rawStorageClient.isVersioningEnabled()).thenReturn(true);
        when(rawStorageClient.put(any(), any())).thenReturn("v1");
    }

    @Test
    void firstRunStoresEverything() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);

        List<SyncRun> runs = parliamentSyncService.sync(SyncTrigger.MANUAL, false);

        assertCounts(runs.get(0), 17, 7, 0, 0, 0, 0);
        assertCounts(runs.get(1), 16, 4, 0, 0, 1, 0);
        assertEquals(ARCHIVE, runs.get(0).getArchiveLastModified());
        verify(rawStorageClient, times(11)).put(startsWith("assemblee-nationale/scrutins/VTANR5L1"), any());

        assertEquals(11, count("SELECT count(*) FROM parliament.parliament_vote"));
        assertEquals(expectedRepresentativeVotes(), count("SELECT count(*) FROM parliament.representative_vote"));
        assertEquals(7 * 12 + 4 * 11, count("SELECT count(*) FROM parliament.group_vote")); // L16 had 11 groups
        // Mises au point: V6 4, V12 1, V213 2, V1992 3 (two of them malfunctions) in L17; V3 5 in L16.
        assertEquals(4 + 1 + 2 + 3 + 5, count("SELECT count(*) FROM parliament.representative_vote_correction"));

        Map<String, Object> scrutin = jdbcTemplate.queryForMap("""
                SELECT jurisdiction_code, chamber_code, vote_type, result, kind, vote_date::text, count_against, raw_s3_key, raw_s3_version_id
                FROM parliament.parliament_vote WHERE external_id = 'VTANR5L17V12'
                """);
        assertEquals(Map.of("jurisdiction_code", "FR", "chamber_code", "AN", "vote_type", "SPO", "result", "ADOPTED",
                "kind", "ARTICLE", "vote_date", "2024-10-22", "count_against", 3,
                "raw_s3_key", "assemblee-nationale/scrutins/VTANR5L17V12.json", "raw_s3_version_id", "v1"), scrutin);
        assertEquals(Map.of("position", "AGAINST", "group_external_id", "PO845401", "by_delegation", false), jdbcTemplate.queryForMap("""
                SELECT position, group_external_id, by_delegation FROM parliament.representative_vote vote
                JOIN parliament.parliament_vote scrutin ON scrutin.id = vote.parliament_vote_id
                WHERE scrutin.external_id = 'VTANR5L17V12' AND vote.mandate_external_id = 'PM842579'
                """));
        assertEquals(12, count("""
                SELECT count(*) FROM parliament.group_vote vote JOIN parliament.parliament_vote scrutin ON scrutin.id = vote.parliament_vote_id
                WHERE scrutin.external_id = 'VTANR5L17V1' AND vote.majority_position IS NULL
                """));
    }

    @Test
    void censureMotionsBecomeBallots() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);

        parliamentSyncService.sync(SyncTrigger.MANUAL, false);
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);

        assertEquals(List.of("VTANR5L16V1", "VTANR5L17V1"), jdbcTemplate.queryForList("""
                SELECT ballot.external_id FROM ballot.ballot ballot
                JOIN ballot.ballot_parliament_vote link ON link.ballot_id = ballot.id
                JOIN parliament.parliament_vote vote ON vote.id = link.parliament_vote_id AND vote.external_id = ballot.external_id
                WHERE ballot.kind = 'CENSURE_MOTION' AND ballot.status = 'OPEN' AND link.is_decisive AND link.alignment = 'SAME'
                ORDER BY 1
                """, String.class));
    }

    @Test
    void lawsBecomeBallotsDecidedByTheirLastVoteOnTheWholeText() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);
        UUID votedLaw = UUID.randomUUID();
        UUID lawPassedBy493 = UUID.randomUUID();
        UUID lawDecidedAtTheSenate = UUID.randomUUID();
        when(lawVoteLinkClient.findAll()).thenReturn(List.of(
                link(votedLaw, "VTANR5L16V1927", LawVoteLink.Kind.TEXT, false),
                link(votedLaw, "VTANR5L16V81", LawVoteLink.Kind.TEXT, true),
                link(lawPassedBy493, "VTANR5L16V1", LawVoteLink.Kind.MOTION, false),
                link(lawPassedBy493, "VTANR5L17V1", LawVoteLink.Kind.MOTION, false),
                new LawVoteLink(lawDecidedAtTheSenate, "senat", "VTSNR5L17V1", LawVoteLink.Kind.TEXT, true)));

        parliamentSyncService.sync(SyncTrigger.MANUAL, false);
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);

        assertEquals(3, count("SELECT count(*) FROM ballot.ballot WHERE kind = 'LAW_FINAL_VOTE'"));
        assertEquals(4, count("""
                SELECT count(*) FROM ballot.ballot_parliament_vote link JOIN ballot.ballot ballot ON ballot.id = link.ballot_id
                WHERE ballot.kind = 'LAW_FINAL_VOTE'
                """));

        BallotRepository.DecisiveVote voted = ballotRepository.findDecisiveVote(votedLaw).orElseThrow();
        assertEquals(LocalDate.of(2022, 7, 21), voted.getVoteDate());
        assertEquals("ADOPTED", voted.getResult());
        assertEquals("SAME", voted.getAlignment());
        assertEquals(List.of(341, 116, 21, 1), List.of(voted.getCountFor(), voted.getCountAgainst(), voted.getCountAbstain(),
                voted.getCountNonVoting()));

        BallotRepository.DecisiveVote by493 = ballotRepository.findDecisiveVote(lawPassedBy493).orElseThrow();
        assertEquals(LocalDate.of(2024, 10, 8), by493.getVoteDate()); // the latest of its two motions
        assertEquals("REJECTED", by493.getResult());
        assertEquals("OPPOSITE", by493.getAlignment());

        assertTrue(ballotRepository.findDecisiveVote(lawDecidedAtTheSenate).isEmpty());
    }

    @Test
    void lawBallotLinksFollowTheLawService() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);
        UUID law = UUID.randomUUID();
        when(lawVoteLinkClient.findAll()).thenReturn(List.of(link(law, "VTANR5L16V1927", LawVoteLink.Kind.TEXT, true)));
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);

        // A new reading: the law service now names another decisive scrutin.
        when(lawVoteLinkClient.findAll()).thenReturn(List.of(
                link(law, "VTANR5L16V1927", LawVoteLink.Kind.TEXT, false),
                link(law, "VTANR5L16V81", LawVoteLink.Kind.TEXT, true)));
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);

        assertEquals(LocalDate.of(2022, 7, 21), ballotRepository.findDecisiveVote(law).orElseThrow().getVoteDate());
        assertEquals(1, count("SELECT count(*) FROM ballot.ballot WHERE kind = 'LAW_FINAL_VOTE'"));
    }

    @Test
    void secondRunSkipsEverything() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);
        clearInvocations(rawStorageClient);

        List<SyncRun> runs = parliamentSyncService.sync(SyncTrigger.SCHEDULED, false);

        assertCounts(runs.get(0), 17, 0, 0, 7, 0, 0);
        assertCounts(runs.get(1), 16, 0, 0, 4, 1, 0);
        verify(rawStorageClient, never()).put(any(), any());
    }

    @Test
    void changedScrutinIsReplacedAndKeepsItsBallot() {
        serve(L17_URL, ScrutinFixtures.L17, null, null);
        serve(L16_URL, ScrutinFixtures.L16, null, null);
        parliamentSyncService.sync(SyncTrigger.MANUAL, false);
        Object ballotLink = jdbcTemplate.queryForObject("SELECT parliament_vote_id FROM ballot.ballot_parliament_vote "
                + "JOIN ballot.ballot ON ballot.id = ballot_id WHERE external_id = 'VTANR5L17V1'", Object.class);

        // Any change to the file reprocesses it, even outside the fields the job reads (here the room, lieuVote).
        String changed = new String(ScrutinFixtures.read("VTANR5L17V1"), StandardCharsets.UTF_8).replace("\"Salons\"", "\"H\\u00e9micycle\"");
        serve(L17_URL, ScrutinFixtures.L17, "VTANR5L17V1", changed.getBytes(StandardCharsets.UTF_8));
        when(rawStorageClient.put(any(), any())).thenReturn("v2");

        SyncRun run = parliamentSyncService.sync(SyncTrigger.MANUAL, false).getFirst();

        assertCounts(run, 17, 0, 1, 6, 0, 0);
        assertEquals("v2", jdbcTemplate.queryForObject(
                "SELECT raw_s3_version_id FROM parliament.parliament_vote WHERE external_id = 'VTANR5L17V1'", String.class));
        assertEquals(197 + 21, count("""
                SELECT count(*) FROM parliament.representative_vote vote JOIN parliament.parliament_vote scrutin ON scrutin.id = vote.parliament_vote_id
                WHERE scrutin.external_id = 'VTANR5L17V1'
                """));
        assertEquals(ballotLink, jdbcTemplate.queryForObject("SELECT parliament_vote_id FROM ballot.ballot_parliament_vote "
                + "JOIN ballot.ballot ON ballot.id = ballot_id WHERE external_id = 'VTANR5L17V1'", Object.class));
    }

    @Test
    void jobRoleCannotReachCitizensVotes() {
        assertDenied("vote_job", "SELECT count(*) FROM citizen.user_vote");
        assertDenied("vote_job", "SELECT count(*) FROM citizen.ballot_user_tally");
        assertAllowed("vote_job", "SELECT count(*) FROM parliament.representative_vote");
        assertAllowed("vote_job", "SELECT count(*) FROM ballot.ballot");
    }

    @Test
    void apiRoleOnlyReadsParliament() {
        assertAllowed("vote_api", "SELECT count(*) FROM parliament.representative_vote");
        assertAllowed("vote_api", "SELECT count(*) FROM citizen.user_vote");
        assertDenied("vote_api", "DELETE FROM parliament.representative_vote");
        assertDenied("vote_api", "DELETE FROM ballot.ballot");
    }

    // A new file for every download: the service deletes the archive once a run is over.
    private void serve(String url, List<String> uids, String replacedUid, byte[] replacement) {
        when(archiveClient.download(url)).thenAnswer(invocation -> new ArchiveDownload(
                ScrutinFixtures.zip(tempDir, System.nanoTime() + ".zip", uids, replacedUid, replacement), ARCHIVE));
    }

    private static LawVoteLink link(UUID lawId, String scrutin, LawVoteLink.Kind kind, boolean decisive) {
        return new LawVoteLink(lawId, "assemblee-nationale", scrutin, kind, decisive);
    }

    private static void assertCounts(SyncRun run, int legislature, int created, int updated, int unchanged, int ignored, int failed) {
        assertEquals(legislature, run.getLegislature());
        assertEquals(List.of(created, updated, unchanged, ignored, failed),
                List.of(run.getCreated(), run.getUpdated(), run.getUnchanged(), run.getIgnored(), run.getFailed()));
    }

    private static long expectedRepresentativeVotes() {
        ScrutinParser parser = new ScrutinParser(new ScrutinKindClassifier());
        return Stream.concat(ScrutinFixtures.L17.stream(), ScrutinFixtures.L16.stream())
                .filter(uid -> uid.startsWith("VTAN"))
                .mapToLong(uid -> parser.parse(ScrutinFixtures.read(uid)).votes().size())
                .sum();
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    // SET LOCAL ROLE only lasts for the transaction, so the pooled connection gets its own role back.
    private void assertDenied(String role, String sql) {
        Exception e = assertThrows(Exception.class, () -> asRole(role, sql));
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        assertTrue(message.contains("permission denied"), message);
    }

    private void assertAllowed(String role, String sql) {
        assertDoesNotThrow(() -> asRole(role, sql));
    }

    private void asRole(String role, String sql) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE " + role);
            jdbcTemplate.execute(sql);
            status.setRollbackOnly();
        });
    }

}
