package com.votingbooth.vote.api.config;

import com.votingbooth.vote.api.dataaccess.repository.VoteRepository;
import com.votingbooth.vote.api.model.entity.Vote;
import com.votingbooth.vote.core.dataaccess.repository.BallotRepository;
import com.votingbooth.vote.core.model.entity.Ballot;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// The API's own entities and repositories, plus vote-core's (ballots and the scrutins that decide them).
@Configuration
@EntityScan(basePackageClasses = {Vote.class, Ballot.class})
@EnableJpaRepositories(basePackageClasses = {VoteRepository.class, BallotRepository.class})
public class JpaConfig {
}
