package com.votingbooth.vote.job.model.sync;

import com.votingbooth.vote.core.model.entity.NonVotingReason;
import com.votingbooth.vote.core.model.entity.VoteKind;
import com.votingbooth.vote.core.model.entity.VotePosition;
import com.votingbooth.vote.core.model.entity.VoteResult;
import com.votingbooth.vote.core.model.entity.VoteType;

import java.time.LocalDate;
import java.util.List;

// A scrutin in our vocabulary, ready to be written to the parliament schema.
public record ParsedScrutin(String externalId, int legislature, int number, LocalDate date, String sessionRef,
                            String sittingRef, VoteType voteType, VoteResult result, String title, String normalizedTitle,
                            VoteKind kind, String requester, String dossierExternalId, int voters, int expressed,
                            int requiredMajority, int countFor, int countAgainst, int countAbstain, int countNonVoting,
                            List<GroupVote> groups, List<RepresentativeVote> votes, List<Correction> corrections) {

    // majorityPosition is null on a censure motion.
    public record GroupVote(String groupExternalId, int groupSize, VotePosition majorityPosition, int countFor,
                            int countAgainst, int countAbstain, int countNonVoting) {
    }

    // groupExternalId is null when the scrutin doesn't tell its groups apart (groups is then empty).
    public record RepresentativeVote(String mandateExternalId, String personExternalId, String groupExternalId,
                                     VotePosition position, boolean byDelegation, NonVotingReason nonVotingReason) {
    }

    public record Correction(String mandateExternalId, String personExternalId, VotePosition declaredPosition,
                             boolean malfunction) {
    }

}
