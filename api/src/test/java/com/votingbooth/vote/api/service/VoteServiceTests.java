package com.votingbooth.vote.api.service;

import com.votingbooth.vote.api.model.law.Law;
import com.votingbooth.vote.api.dataaccess.client.law.LawClient;
import com.votingbooth.vote.api.model.law.LawResponse;
import com.votingbooth.vote.api.model.law.LawStatus;
import com.votingbooth.vote.api.model.dto.VoteRequest;
import com.votingbooth.vote.api.model.dto.VoteResultResponse;
import com.votingbooth.vote.api.model.exception.LawDoesNotExistException;
import com.votingbooth.vote.api.model.exception.UserAlreadyVotedException;
import com.votingbooth.vote.api.dataaccess.repository.VoteRepository;
import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import com.votingbooth.vote.core.model.entity.VoteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VoteServiceTests {

    private VoteRepository voteRepository;
    private BallotRepository ballotRepository;
    private LawClient lawClient;
    private VoteService voteService;

    private final UUID userId = UUID.randomUUID();
    private final UUID lawId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        voteRepository = mock(VoteRepository.class);
        ballotRepository = mock(BallotRepository.class);
        lawClient = mock(LawClient.class);
        voteService = new VoteService(voteRepository, ballotRepository, lawClient, 3);
        when(ballotRepository.findDecisiveVote(any())).thenReturn(Optional.empty());
    }

    @Test
    void vote() {
        when(lawClient.getLaw(lawId)).thenReturn(new LawResponse(new Law(lawId, LawStatus.VOTING)));

        voteService.vote(new VoteRequest(userId, lawId, 1));

        verify(voteRepository).create(userId, lawId, 1);
    }

    @Test
    void duplicatedVote() {
        when(lawClient.getLaw(lawId)).thenReturn(new LawResponse(new Law(lawId, LawStatus.VOTING)));
        doThrow(DataIntegrityViolationException.class).when(voteRepository).create(userId, lawId, 1);

        assertThrows(UserAlreadyVotedException.class, () -> voteService.vote(new VoteRequest(userId, lawId, 1)));
    }

    @Test
    void lawDoesNotExist() {
        when(lawClient.getLaw(lawId)).thenReturn(null);

        assertThrows(LawDoesNotExistException.class, () -> voteService.vote(new VoteRequest(userId, lawId, 1)));
        verifyNoInteractions(voteRepository);
    }

    @Test
    void voteResult() {
        when(voteRepository.countByLawId(lawId)).thenReturn(counts(2, 1, 1));
        when(ballotRepository.findDecisiveVote(lawId)).thenReturn(Optional.of(decisiveVote("ADOPTED", "SAME")));

        assertEquals(new VoteResultResponse(lawId,
                new VoteResultResponse.RepresentativeResult(LocalDate.of(2022, 7, 21), VoteResult.ADOPTED, 341, 116, 21, 1, false),
                new VoteResultResponse.UserResult(2, 1, 1)), voteService.getVoteResult(lawId));
    }

    @Test
    void lawPassedByCensureMotionRejection() {
        when(voteRepository.countByLawId(lawId)).thenReturn(counts(0, 0, 0));
        when(ballotRepository.findDecisiveVote(lawId)).thenReturn(Optional.of(decisiveVote("REJECTED", "OPPOSITE")));

        VoteResultResponse.RepresentativeResult result = voteService.getVoteResult(lawId).representativeResult();

        assertEquals(VoteResult.ADOPTED, result.result());
        assertTrue(result.decidedByCensureMotion());
    }

    @Test
    void notVotedByRepresentativesYet() {
        when(voteRepository.countByLawId(lawId)).thenReturn(counts(2, 1, 1));

        assertEquals(new VoteResultResponse(lawId, null, new VoteResultResponse.UserResult(2, 1, 1)),
                voteService.getVoteResult(lawId));
    }

    @Test
    void tooFewUserVotesToShow() {
        when(voteRepository.countByLawId(lawId)).thenReturn(counts(1, 1, 0));

        assertNull(voteService.getVoteResult(lawId).userResult());
    }

    @Test
    void noVotesYet() {
        when(voteRepository.countByLawId(lawId)).thenReturn(counts(0, 0, 0));

        assertEquals(new VoteResultResponse(lawId, null, null), voteService.getVoteResult(lawId));
    }

    private static BallotRepository.DecisiveVote decisiveVote(String result, String alignment) {
        return new BallotRepository.DecisiveVote() {
            public LocalDate getVoteDate() { return LocalDate.of(2022, 7, 21); }
            public String getResult() { return result; }
            public String getAlignment() { return alignment; }
            public int getCountFor() { return 341; }
            public int getCountAgainst() { return 116; }
            public int getCountAbstain() { return 21; }
            public int getCountNonVoting() { return 1; }
        };
    }

    private static VoteRepository.VoteCounts counts(long yes, long no, long abstain) {
        return new VoteRepository.VoteCounts() {
            public long getYes() { return yes; }
            public long getNo() { return no; }
            public long getAbstain() { return abstain; }
        };
    }
}
