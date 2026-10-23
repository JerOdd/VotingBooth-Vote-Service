package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// Censure motions need no law: each one is a ballot of its own ("censure the government?"), decided by its scrutin.
// When law ballots arrive from the law service, a law passed by 49.3 will also link the motion, with alignment OPPOSITE.
@Service
public class CensureBallotService {

    private static final Logger log = LoggerFactory.getLogger(CensureBallotService.class);

    private final BallotRepository ballotRepository;
    private final Clock clock;

    public CensureBallotService(BallotRepository ballotRepository, Clock clock) {
        this.ballotRepository = ballotRepository;
        this.clock = clock;
    }

    @Transactional
    public int createBallots(String source) {
        int created = ballotRepository.createCensureMotionBallots(source, LocalDateTime.now(clock));
        int linked = ballotRepository.linkCensureMotionBallots(source);
        log.info("Censure motion ballots: {} created, {} linked to their scrutin", created, linked);
        return created;
    }

}
