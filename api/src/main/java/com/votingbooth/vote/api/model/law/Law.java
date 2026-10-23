package com.votingbooth.vote.api.model.law;

import java.util.UUID;

public class Law {
    private UUID lawId;
    private LawStatus status;

    public Law() {}
    public Law(UUID lawId, LawStatus status) {
        this.lawId = lawId;
        this.status = status;
    }

    public UUID getLawId() {
        return lawId;
    }

    public LawStatus getStatus() {
        return status;
    }
}
