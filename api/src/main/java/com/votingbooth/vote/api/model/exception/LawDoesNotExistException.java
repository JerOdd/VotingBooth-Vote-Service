package com.votingbooth.vote.api.model.exception;

import java.util.UUID;

public class LawDoesNotExistException extends RuntimeException {
    public LawDoesNotExistException(UUID lawId) {
        super(lawId + " doesn't exist.");
    }
}
