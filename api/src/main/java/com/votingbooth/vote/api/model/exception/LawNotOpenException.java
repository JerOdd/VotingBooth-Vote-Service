package com.votingbooth.vote.api.model.exception;

import java.util.UUID;

public class LawNotOpenException extends RuntimeException {
    public LawNotOpenException(UUID lawId) {
        super(lawId + " is not open.");
    }
}
