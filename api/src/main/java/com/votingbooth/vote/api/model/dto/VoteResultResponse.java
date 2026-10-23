package com.votingbooth.vote.api.model.dto;

public record VoteResultResponse(long yes, long no, long abstain) {

    public long total() {
        return yes + no + abstain;
    }
}
