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
@Table(name = "ballot", schema = "ballot")
public class Ballot {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private BallotKind kind;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BallotStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Ballot() {
    }

    public UUID getId() {
        return id;
    }

    public BallotKind getKind() {
        return kind;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getOpensAt() {
        return opensAt;
    }

    public BallotStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
