package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import com.votingbooth.vote.core.model.entity.VoteAlignment;
import com.votingbooth.vote.job.dataaccess.client.law.LawVoteLinkClient;
import com.votingbooth.vote.job.model.law.LawVoteLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// One ballot per law the law service links to scrutins, by the law's id in the law service. Its links are rebuilt on every
// run. A vote for a motion (censure, rejection) is a vote against the law, so motions count with alignment OPPOSITE.
@Service
public class LawBallotService {

    private static final Logger log = LoggerFactory.getLogger(LawBallotService.class);

    private final LawVoteLinkClient lawVoteLinkClient;
    private final BallotRepository ballotRepository;
    private final Clock clock;

    public LawBallotService(LawVoteLinkClient lawVoteLinkClient, BallotRepository ballotRepository, Clock clock) {
        this.lawVoteLinkClient = lawVoteLinkClient;
        this.ballotRepository = ballotRepository;
        this.clock = clock;
    }

    @Transactional
    public void syncBallots() {
        Map<UUID, List<LawVoteLink>> linksByLaw = lawVoteLinkClient.findAll().stream()
                .collect(Collectors.groupingBy(LawVoteLink::lawId));
        LocalDateTime now = LocalDateTime.now(clock);
        int created = 0;
        int linked = 0;
        int byMotion = 0;
        ballotRepository.deleteLawBallotLinks();
        for (Map.Entry<UUID, List<LawVoteLink>> law : linksByLaw.entrySet()) {
            created += ballotRepository.createLawBallot(law.getKey(), now);
            for (LawVoteLink link : law.getValue()) {
                VoteAlignment alignment = link.kind() == LawVoteLink.Kind.MOTION ? VoteAlignment.OPPOSITE : VoteAlignment.SAME;
                linked += ballotRepository.linkLawBallot(law.getKey(), link.source(), link.scrutinExternalId(),
                        link.decisive(), alignment.name());
            }
            // Only when the law service names no decisive vote: a decisive Senate vote isn't replaced by a motion.
            if (law.getValue().stream().noneMatch(LawVoteLink::decisive)) {
                byMotion += ballotRepository.markLatestMotionDecisive(law.getKey());
            }
        }
        log.info("Law ballots: {} laws, {} ballots created, {} scrutins linked, {} decided by a censure motion",
                linksByLaw.size(), created, linked, byMotion);
    }

}
