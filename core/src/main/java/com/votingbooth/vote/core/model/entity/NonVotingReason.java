package com.votingbooth.vote.core.model.entity;

// Why a representative listed as non-voting couldn't vote (the source's causePositionVote: MG, PAN, PSE).
public enum NonVotingReason {
    GOVERNMENT_MEMBER,
    ASSEMBLY_PRESIDENT,
    SITTING_PRESIDENT
}
