package com.votingbooth.vote.api.model.exception;

import java.util.UUID;

public class LawNotVotedException extends RuntimeException {
    public LawNotVotedException(UUID lawId) {
        super(lawId + " has no results yet.");
    }
}
