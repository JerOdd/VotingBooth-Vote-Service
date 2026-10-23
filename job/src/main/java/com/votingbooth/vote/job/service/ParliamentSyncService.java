package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.dataaccess.repository.SyncRunRepository;
import com.votingbooth.vote.core.model.entity.SyncRun;
import com.votingbooth.vote.core.model.entity.SyncStatus;
import com.votingbooth.vote.core.model.entity.SyncTrigger;
import com.votingbooth.vote.job.dataaccess.client.assembleenationale.ScrutinArchiveClient;
import com.votingbooth.vote.job.dataaccess.client.storage.RawStorageClient;
import com.votingbooth.vote.job.dataaccess.repository.ParliamentVoteJdbcRepository;
import com.votingbooth.vote.job.model.scrutin.ArchiveDownload;
import com.votingbooth.vote.job.model.scrutin.ArchiveEntry;
import com.votingbooth.vote.job.model.sync.ParsedScrutin;
import com.votingbooth.vote.job.model.sync.SyncCounts;
import com.votingbooth.vote.job.utils.ScrutinArchiveReader;
import com.votingbooth.vote.job.utils.ScrutinParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Nightly sync of the Assemblée nationale's "Scrutins" archives, one per configured legislature, then of the ballots: censure
// motions, and laws from the law service's links. Each file is hashed: an unchanged file is skipped, a changed one is parsed, stored raw in S3, then its scrutin
// and all its rows are replaced. An invalid file is counted as failed without failing the run.
@Service
public class ParliamentSyncService {

    private static final Logger log = LoggerFactory.getLogger(ParliamentSyncService.class);

    static final String SOURCE = "assemblee-nationale";
    // Assemblée nationale scrutins (VTAN...). The L16 archive also holds a Congrès scrutin (VTCG...), with senators.
    private static final String CHAMBER_PREFIX = "VTAN";
    private static final String STORAGE_FOLDER = "scrutins";

    private final ScrutinArchiveClient archiveClient;
    private final RawStorageClient rawStorageClient;
    private final ScrutinArchiveReader archiveReader;
    private final ScrutinParser parser;
    private final ScrutinSyncService scrutinSyncService;
    private final CensureBallotService censureBallotService;
    private final LawBallotService lawBallotService;
    private final ParliamentVoteJdbcRepository parliamentVoteRepository;
    private final SyncRunRepository syncRunRepository;
    private final Clock clock;
    private final List<Integer> legislatures;
    private final String archiveUrlTemplate;
    private final boolean requireVersioning;
    private final int staleArchiveAttempts;
    private final Duration staleArchiveRetryDelay;

    public ParliamentSyncService(ScrutinArchiveClient archiveClient, RawStorageClient rawStorageClient,
                                 ScrutinArchiveReader archiveReader, ScrutinParser parser,
                                 ScrutinSyncService scrutinSyncService, CensureBallotService censureBallotService,
                                 LawBallotService lawBallotService,
                                 ParliamentVoteJdbcRepository parliamentVoteRepository, SyncRunRepository syncRunRepository,
                                 Clock clock,
                                 @Value("${assemblee-nationale.legislatures}") List<Integer> legislatures,
                                 @Value("${assemblee-nationale.scrutins-archive-url}") String archiveUrlTemplate,
                                 @Value("${storage.require-versioning:true}") boolean requireVersioning,
                                 @Value("${assemblee-nationale.stale-archive-attempts:3}") int staleArchiveAttempts,
                                 @Value("${assemblee-nationale.stale-archive-retry-delay:PT1M}") Duration staleArchiveRetryDelay) {
        this.archiveClient = archiveClient;
        this.rawStorageClient = rawStorageClient;
        this.archiveReader = archiveReader;
        this.parser = parser;
        this.scrutinSyncService = scrutinSyncService;
        this.censureBallotService = censureBallotService;
        this.lawBallotService = lawBallotService;
        this.parliamentVoteRepository = parliamentVoteRepository;
        this.syncRunRepository = syncRunRepository;
        this.clock = clock;
        this.legislatures = legislatures;
        this.archiveUrlTemplate = archiveUrlTemplate;
        this.requireVersioning = requireVersioning;
        this.staleArchiveAttempts = staleArchiveAttempts;
        this.staleArchiveRetryDelay = staleArchiveRetryDelay;
    }

    // force ignores every stored hash, so all scrutins are processed again (e.g. after a mapping change).
    // A failed legislature doesn't stop the others; the call then throws, so a run-once job exits with an error.
    public List<SyncRun> sync(SyncTrigger trigger, boolean force) {
        if (requireVersioning && !rawStorageClient.isVersioningEnabled()) {
            throw new IllegalStateException("Bucket versioning is off, so raw files would be overwritten. "
                    + "Enable it on the bucket, or set storage.require-versioning=false for a local run.");
        }
        List<SyncRun> runs = new ArrayList<>();
        for (int legislature : legislatures) {
            runs.add(syncLegislature(legislature, trigger, force));
        }
        censureBallotService.createBallots(SOURCE);
        // After the scrutins, so that new ones can be linked. If the law service is down, the job fails here.
        lawBallotService.syncBallots();
        List<Integer> failed = runs.stream().filter(run -> run.getStatus() == SyncStatus.FAILED).map(SyncRun::getLegislature).toList();
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Sync failed for legislatures " + failed + ", see their sync_run");
        }
        return runs;
    }

    private SyncRun syncLegislature(int legislature, SyncTrigger trigger, boolean force) {
        String archiveUrl = archiveUrlTemplate.replace("{legislature}", Integer.toString(legislature));
        SyncRun run = syncRunRepository.save(new SyncRun(SOURCE, trigger, legislature, archiveUrl, now()));
        log.info("Sync run {} started for {} (force: {})", run.getId(), archiveUrl, force);

        Path archive = null;
        try {
            Optional<LocalDateTime> latestApplied = syncRunRepository.findLatestAppliedArchive(SOURCE, archiveUrl);
            Optional<ArchiveDownload> download = downloadNotOlderThan(archiveUrl, latestApplied.orElse(null));
            if (download.isEmpty()) {
                String reason = "every download was older than the archive of " + latestApplied.get() + " UTC already applied";
                run.skip(reason, now());
                log.warn("Sync run {} skipped: {}", run.getId(), reason);
                return syncRunRepository.save(run);
            }
            archive = download.get().file();
            run.recordArchive(sha256(archive), download.get().lastModified());

            Map<String, String> knownHashes = parliamentVoteRepository.findHashes(SOURCE, legislature);
            SyncCounts counts = new SyncCounts();
            archiveReader.forEach(archive, entry -> apply(entry, knownHashes.get(entry.externalId()), force, counts));

            run.succeed(counts.getCreated(), counts.getUpdated(), counts.getUnchanged(), counts.getIgnored(),
                    counts.getFailed(), now());
            log.info("Sync run {} succeeded: {} created, {} updated, {} unchanged, {} ignored, {} failed; {} representative votes written",
                    run.getId(), counts.getCreated(), counts.getUpdated(), counts.getUnchanged(), counts.getIgnored(),
                    counts.getFailed(), counts.getRepresentativeVotes());
            return syncRunRepository.save(run);
        } catch (RuntimeException e) {
            run.fail(e.toString(), now());
            log.error("Sync run {} failed", run.getId(), e);
            return syncRunRepository.save(run);
        } finally {
            deleteQuietly(archive);
        }
    }

    // The Assemblée's servers don't all hold the latest archive: a download can be hours older than the previous one.
    // Applying it would roll scrutins back, so it's retried, then the run is skipped.
    private Optional<ArchiveDownload> downloadNotOlderThan(String archiveUrl, LocalDateTime latestApplied) {
        for (int attempt = 1; attempt <= staleArchiveAttempts; attempt++) {
            ArchiveDownload download = archiveClient.download(archiveUrl);
            if (latestApplied == null || download.lastModified() == null || !download.lastModified().isBefore(latestApplied)) {
                return Optional.of(download);
            }
            log.warn("Downloaded archive of {} UTC is older than the one of {} UTC already applied (attempt {}/{})",
                    download.lastModified(), latestApplied, attempt, staleArchiveAttempts);
            deleteQuietly(download.file());
            if (attempt < staleArchiveAttempts) {
                pause();
            }
        }
        return Optional.empty();
    }

    // Parsed before anything is stored, so an invalid file never adds an S3 version every night. A storage error isn't about
    // the scrutin: it propagates and fails the run, instead of failing every scrutin one by one.
    private void apply(ArchiveEntry entry, String knownHash, boolean force, SyncCounts counts) {
        if (!entry.externalId().startsWith(CHAMBER_PREFIX)) {
            counts.ignored();
            log.info("Ignored {}: not an Assemblée nationale scrutin", entry.externalId());
            return;
        }
        String sha256 = sha256(entry.content());
        boolean sameContent = sha256.equals(knownHash);
        if (sameContent && !force) {
            counts.unchanged();
            return;
        }
        ParsedScrutin scrutin;
        try {
            scrutin = parser.parse(entry.content());
            if (!scrutin.externalId().equals(entry.externalId())) {
                throw new IllegalArgumentException("File " + entry.externalId() + " holds scrutin " + scrutin.externalId());
            }
        } catch (RuntimeException e) {
            counts.failed();
            log.warn("Could not parse scrutin {}: {}", entry.externalId(), e.toString());
            return;
        }
        if (scrutin.groups().isEmpty()) {
            log.warn("Scrutin {} doesn't tell its groups apart: its votes are stored without group", entry.externalId());
        }
        String key = SOURCE + "/" + STORAGE_FOLDER + "/" + entry.externalId() + ".json";
        String versionId = sameContent ? null : rawStorageClient.put(key, entry.content());
        try {
            scrutinSyncService.sync(SOURCE, scrutin, key, versionId, sha256, now());
        } catch (RuntimeException e) {
            counts.failed();
            log.warn("Could not sync scrutin {}: {}", entry.externalId(), e.toString());
            return;
        }
        if (knownHash == null) {
            counts.created(scrutin.votes().size());
        } else {
            counts.updated(scrutin.votes().size());
        }
    }

    private void pause() {
        try {
            Thread.sleep(staleArchiveRetryDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to download the archive again", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String sha256(byte[] content) {
        return HexFormat.of().formatHex(digest().digest(content));
    }

    private static String sha256(Path file) {
        MessageDigest digest = digest();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot hash " + file, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary archive {}", file);
        }
    }

}
