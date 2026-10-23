package com.votingbooth.vote.api.model.dto;

import com.votingbooth.vote.core.model.entity.VoteResult;

import java.time.LocalDate;
import java.util.UUID;

// A law's results: how the Assemblée decided it, and how citizens voted. Either is null when there is none to show.
public record VoteResultResponse(UUID lawId, RepresentativeResult representativeResult, UserResult userResult) {

    // The decisive scrutin. For a law passed by 49.3, it's the censure motion: its counts are the votes for the censure,
    // and result is the law's (a rejected motion leaves the law adopted).
    public record RepresentativeResult(LocalDate voteDate, VoteResult result, long yes, long no, long abstain,
                                       long nonVoting, boolean decidedByCensureMotion) {
    }

    public record UserResult(long yes, long no, long abstain) {

        public long total() {
            return yes + no + abstain;
        }
    }

}
