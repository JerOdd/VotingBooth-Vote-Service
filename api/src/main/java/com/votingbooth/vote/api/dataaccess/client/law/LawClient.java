package com.votingbooth.vote.api.dataaccess.client.law;

import com.votingbooth.vote.api.model.law.LawResponse;

import java.util.UUID;

public interface LawClient {
    LawResponse getLaw(UUID lawId);
}
