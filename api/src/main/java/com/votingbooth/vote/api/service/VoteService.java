package com.votingbooth.vote.api.service;

import com.votingbooth.vote.api.dataaccess.client.law.LawClient;
import com.votingbooth.vote.api.dataaccess.repository.VoteRepository;
import com.votingbooth.vote.api.model.dto.VoteRequest;
import com.votingbooth.vote.api.model.dto.VoteResultResponse;
import com.votingbooth.vote.api.model.exception.LawDoesNotExistException;
import com.votingbooth.vote.api.model.exception.LawNotOpenException;
import com.votingbooth.vote.api.model.exception.UserAlreadyVotedException;
import com.votingbooth.vote.api.model.law.Law;
import com.votingbooth.vote.api.model.law.LawResponse;
import com.votingbooth.vote.api.model.law.LawStatus;
import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import com.votingbooth.vote.core.model.entity.VoteAlignment;
import com.votingbooth.vote.core.model.entity.VoteResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VoteService {

    private final VoteRepository voteRepository;
    private final BallotRepository ballotRepository;
    private final LawClient lawClient;
    private final long userResultMinVotes;

    public VoteService(VoteRepository voteRepository, BallotRepository ballotRepository, LawClient lawClient,
                       @Value("${vote.user-result.min-votes}") long userResultMinVotes) {
        this.voteRepository = voteRepository;
        this.ballotRepository = ballotRepository;
        this.lawClient = lawClient;
        this.userResultMinVotes = userResultMinVotes;
    }

    public void vote(VoteRequest request) {
        LawResponse lawResponse = lawClient.getLaw(request.lawId());
        if (lawResponse == null || lawResponse.getLaw() == null) {
            throw new LawDoesNotExistException(request.lawId());
        }
        Law law = lawResponse.getLaw();
        if (law.getStatus() != LawStatus.VOTING) {
            throw new LawNotOpenException(request.lawId());
        }
        try {
            voteRepository.create(request.userId(), request.lawId(), request.value());
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyVotedException(request.userId(), request.lawId());
        }
    }

    public VoteResultResponse getVoteResult(UUID lawId) {
        return new VoteResultResponse(lawId, representativeResult(lawId), userResult(lawId));
    }

    private VoteResultResponse.RepresentativeResult representativeResult(UUID lawId) {
        return ballotRepository.findDecisiveVote(lawId)
                .map(vote -> {
                    boolean censureMotion = VoteAlignment.valueOf(vote.getAlignment()) == VoteAlignment.OPPOSITE;
                    VoteResult result = VoteResult.valueOf(vote.getResult());
                    if (censureMotion) {
                        result = result == VoteResult.ADOPTED ? VoteResult.REJECTED : VoteResult.ADOPTED;
                    }
                    return new VoteResultResponse.RepresentativeResult(vote.getVoteDate(), result, vote.getCountFor(),
                            vote.getCountAgainst(), vote.getCountAbstain(), vote.getCountNonVoting(), censureMotion);
                })
                .orElse(null);
    }

    // Citizens' votes are political opinions: their counts are only shown above a minimum number of votes.
    private VoteResultResponse.UserResult userResult(UUID lawId) {
        VoteRepository.VoteCounts counts = voteRepository.countByLawId(lawId);
        VoteResultResponse.UserResult result = new VoteResultResponse.UserResult(counts.getYes(), counts.getNo(), counts.getAbstain());
        return result.total() < userResultMinVotes || result.total() == 0 ? null : result;
    }

}
