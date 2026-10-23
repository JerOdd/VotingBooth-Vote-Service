package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.dataaccess.repository.SyncRunRepository;
import com.votingbooth.vote.core.model.entity.SyncRun;
import com.votingbooth.vote.core.model.entity.SyncStatus;
import com.votingbooth.vote.core.model.entity.SyncTrigger;
import com.votingbooth.vote.job.dataaccess.client.assembleenationale.ScrutinArchiveClient;
import com.votingbooth.vote.job.dataaccess.client.storage.RawStorageClient;
import com.votingbooth.vote.job.dataaccess.repository.ParliamentVoteJdbcRepository;
import com.votingbooth.vote.job.model.scrutin.ArchiveDownload;
import com.votingbooth.vote.job.utils.ScrutinArchiveReader;
import com.votingbooth.vote.job.utils.ScrutinFixtures;
import com.votingbooth.vote.job.utils.ScrutinKindClassifier;
import com.votingbooth.vote.job.utils.ScrutinParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ParliamentSyncServiceTests {

    private static final String L17_URL = "https://example.org/17/Scrutins.json.zip";
    private static final LocalDateTime APPLIED_ARCHIVE = LocalDateTime.of(2026, 10, 23, 4, 25);

    @TempDir
    Path tempDir;

    private ScrutinArchiveClient archiveClient;
    private RawStorageClient rawStorageClient;
    private ScrutinSyncService scrutinSyncService;
    private CensureBallotService censureBallotService;
    private LawBallotService lawBallotService;
    private ParliamentVoteJdbcRepository parliamentVoteRepository;
    private SyncRunRepository syncRunRepository;
    private ParliamentSyncService service;

    @BeforeEach
    void setUp() {
        archiveClient = mock(ScrutinArchiveClient.class);
        rawStorageClient = mock(RawStorageClient.class);
        scrutinSyncService = mock(ScrutinSyncService.class);
        censureBallotService = mock(CensureBallotService.class);
        lawBallotService = mock(LawBallotService.class);
        parliamentVoteRepository = mock(ParliamentVoteJdbcRepository.class);
        syncRunRepository = mock(SyncRunRepository.class);
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRunRepository.findLatestAppliedArchive(any(), any())).thenReturn(Optional.empty());
        when(rawStorageClient.isVersioningEnabled()).thenReturn(true);
        when(rawStorageClient.put(any(), any())).thenReturn("v1");
        service = new ParliamentSyncService(archiveClient, rawStorageClient, new ScrutinArchiveReader(),
                new ScrutinParser(new ScrutinKindClassifier()), scrutinSyncService, censureBallotService, lawBallotService,
                parliamentVoteRepository, syncRunRepository, Clock.fixed(Instant.parse("2026-10-23T02:00:00Z"), ZoneOffset.UTC),
                List.of(17), "https://example.org/{legislature}/Scrutins.json.zip", true, 2, Duration.ZERO);
    }

    @Test
    void newScrutinIsStoredThenSynced() {
        archive(List.of("VTANR5L17V12"), APPLIED_ARCHIVE);

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(SyncStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getCreated());
        String key = "assemblee-nationale/scrutins/VTANR5L17V12.json";
        verify(rawStorageClient).put(key, ScrutinFixtures.read("VTANR5L17V12"));
        verify(scrutinSyncService).sync(eq("assemblee-nationale"), argThat(scrutin -> scrutin.externalId().equals("VTANR5L17V12")),
                eq(key), eq("v1"), eq(sha256(ScrutinFixtures.read("VTANR5L17V12"))), any());
        verify(censureBallotService).createBallots("assemblee-nationale");
        verify(lawBallotService).syncBallots();
    }

    @Test
    void unchangedScrutinIsSkipped() {
        archive(List.of("VTANR5L17V12"), APPLIED_ARCHIVE);
        when(parliamentVoteRepository.findHashes("assemblee-nationale", 17))
                .thenReturn(Map.of("VTANR5L17V12", sha256(ScrutinFixtures.read("VTANR5L17V12"))));

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(1, run.getUnchanged());
        verify(rawStorageClient, never()).put(any(), any());
        verifyNoInteractions(scrutinSyncService);
    }

    @Test
    void forceReprocessesUnchangedScrutinWithoutStoringItAgain() {
        archive(List.of("VTANR5L17V12"), APPLIED_ARCHIVE);
        when(parliamentVoteRepository.findHashes("assemblee-nationale", 17))
                .thenReturn(Map.of("VTANR5L17V12", sha256(ScrutinFixtures.read("VTANR5L17V12"))));

        SyncRun run = service.sync(SyncTrigger.MANUAL, true).getFirst();

        assertEquals(1, run.getUpdated());
        verify(rawStorageClient, never()).put(any(), any());
        verify(scrutinSyncService).sync(any(), any(), any(), isNull(), any(), any());
    }

    @Test
    void congressScrutinIsIgnored() {
        archive(List.of("VTCGR5L16V1"), APPLIED_ARCHIVE);

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(SyncStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getIgnored());
        assertEquals(0, run.getFailed());
        verifyNoInteractions(scrutinSyncService);
    }

    @Test
    void invalidScrutinFailsAloneAndIsNeverStored() {
        byte[] invalid = "{\"scrutin\": {\"uid\": \"VTANR5L17V6\"}}".getBytes(StandardCharsets.UTF_8);
        archive(ScrutinFixtures.zip(tempDir, "a.zip", List.of("VTANR5L17V6", "VTANR5L17V12"), "VTANR5L17V6", invalid), APPLIED_ARCHIVE);

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(SyncStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getFailed());
        assertEquals(1, run.getCreated());
        verify(rawStorageClient, never()).put(eq("assemblee-nationale/scrutins/VTANR5L17V6.json"), any());
    }

    @Test
    void databaseErrorOnOneScrutinDoesNotFailTheRun() {
        archive(List.of("VTANR5L17V6", "VTANR5L17V12"), APPLIED_ARCHIVE);
        doThrow(new IllegalStateException("boom")).when(scrutinSyncService)
                .sync(any(), argThat(scrutin -> scrutin.externalId().equals("VTANR5L17V6")), any(), any(), any(), any());

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(1, run.getFailed());
        assertEquals(1, run.getCreated());
    }

    @Test
    void olderArchiveIsRetriedThenSkipped() {
        when(syncRunRepository.findLatestAppliedArchive("assemblee-nationale", L17_URL)).thenReturn(Optional.of(APPLIED_ARCHIVE));
        when(archiveClient.download(L17_URL)).thenAnswer(invocation -> new ArchiveDownload(
                ScrutinFixtures.zip(tempDir, "old-" + System.nanoTime() + ".zip", List.of("VTANR5L17V12")), APPLIED_ARCHIVE.minusHours(2)));

        SyncRun run = service.sync(SyncTrigger.SCHEDULED, false).getFirst();

        assertEquals(SyncStatus.SKIPPED, run.getStatus());
        verify(archiveClient, times(2)).download(L17_URL);
        verifyNoInteractions(scrutinSyncService);
    }

    @Test
    void failedDownloadFailsTheJobAfterRecordingTheRun() {
        when(archiveClient.download(L17_URL)).thenThrow(new IllegalStateException("HTTP 503"));

        assertThrows(IllegalStateException.class, () -> service.sync(SyncTrigger.SCHEDULED, false));

        verify(syncRunRepository, atLeastOnce()).save(argThat(run -> run.getStatus() == SyncStatus.FAILED));
        verify(censureBallotService).createBallots("assemblee-nationale");
    }

    @Test
    void unversionedBucketIsRefused() {
        when(rawStorageClient.isVersioningEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.sync(SyncTrigger.SCHEDULED, false));
        verifyNoInteractions(archiveClient);
    }

    private void archive(List<String> uids, LocalDateTime lastModified) {
        archive(ScrutinFixtures.zip(tempDir, "archive.zip", uids), lastModified);
    }

    private void archive(Path zip, LocalDateTime lastModified) {
        when(archiveClient.download(L17_URL)).thenReturn(new ArchiveDownload(zip, lastModified));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
