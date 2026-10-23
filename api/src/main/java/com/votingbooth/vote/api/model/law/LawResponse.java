package com.votingbooth.vote.api.model.law;

public class LawResponse {
    private Law law;

    public LawResponse() {}

    public LawResponse(Law law) {
        this.law = law;
    }

    public Law getLaw() {
        return law;
    }
}
