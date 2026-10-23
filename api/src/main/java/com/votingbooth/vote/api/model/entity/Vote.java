package com.votingbooth.vote.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote {

    @EmbeddedId
    private VoteId id;

    @Column(nullable = false)
    private Integer value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Vote() {}

    public UUID getLawId() {
        return id.getLawId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public Integer getValue() {
        return value;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
