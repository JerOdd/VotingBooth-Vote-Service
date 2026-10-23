package com.votingbooth.vote.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class VoteId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "law_id", nullable = false)
    private UUID lawId;

    public VoteId() {}

    public VoteId(UUID userId, UUID lawId) {
        this.userId = userId;
        this.lawId = lawId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoteId)) return false;
        VoteId voteId = (VoteId) o;
        return Objects.equals(userId, voteId.userId) &&
                Objects.equals(lawId, voteId.lawId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, lawId);
    }

    public UUID getLawId() {
        return lawId;
    }

    public UUID getUserId() {
        return userId;
    }
}
