package com.votingbooth.vote.core.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_run", schema = "parliament")
public class SyncRun {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source", nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false)
    private SyncTrigger trigger;

    @Column(name = "legislature", nullable = false)
    private Integer legislature;

    @Column(name = "archive_url", nullable = false)
    private String archiveUrl;

    @Column(name = "archive_sha256", nullable = true)
    private String archiveSha256;

    @Column(name = "archive_last_modified", nullable = true)
    private LocalDateTime archiveLastModified;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = true)
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncStatus status;

    @Column(name = "created", nullable = false)
    private Integer created;

    @Column(name = "updated", nullable = false)
    private Integer updated;

    @Column(name = "unchanged", nullable = false)
    private Integer unchanged;

    @Column(name = "ignored", nullable = false)
    private Integer ignored;

    @Column(name = "failed", nullable = false)
    private Integer failed;

    @Column(name = "error", nullable = true)
    private String error;

    protected SyncRun() {
    }

    public SyncRun(String source, SyncTrigger trigger, int legislature, String archiveUrl, LocalDateTime startedAt) {
        this.id = UUID.randomUUID();
        this.source = source;
        this.trigger = trigger;
        this.legislature = legislature;
        this.archiveUrl = archiveUrl;
        this.startedAt = startedAt;
        this.status = SyncStatus.RUNNING;
        this.created = 0;
        this.updated = 0;
        this.unchanged = 0;
        this.ignored = 0;
        this.failed = 0;
    }

    public void recordArchive(String sha256, LocalDateTime lastModified) {
        this.archiveSha256 = sha256;
        this.archiveLastModified = lastModified;
    }

    public void skip(String reason, LocalDateTime finishedAt) {
        this.status = SyncStatus.SKIPPED;
        this.error = reason;
        this.finishedAt = finishedAt;
    }

    public void succeed(int created, int updated, int unchanged, int ignored, int failed, LocalDateTime finishedAt) {
        this.status = SyncStatus.SUCCEEDED;
        this.created = created;
        this.updated = updated;
        this.unchanged = unchanged;
        this.ignored = ignored;
        this.failed = failed;
        this.finishedAt = finishedAt;
    }

    public void fail(String error, LocalDateTime finishedAt) {
        this.status = SyncStatus.FAILED;
        this.error = error;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public SyncTrigger getTrigger() {
        return trigger;
    }

    public Integer getLegislature() {
        return legislature;
    }

    public String getArchiveUrl() {
        return archiveUrl;
    }

    public String getArchiveSha256() {
        return archiveSha256;
    }

    public LocalDateTime getArchiveLastModified() {
        return archiveLastModified;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public Integer getCreated() {
        return created;
    }

    public Integer getUpdated() {
        return updated;
    }

    public Integer getUnchanged() {
        return unchanged;
    }

    public Integer getIgnored() {
        return ignored;
    }

    public Integer getFailed() {
        return failed;
    }

    public String getError() {
        return error;
    }

}
