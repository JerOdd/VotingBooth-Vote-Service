package com.votingbooth.vote.api.model.dto;

import java.util.UUID;

public record VoteRequest(UUID userId, UUID lawId, Integer value) { }
