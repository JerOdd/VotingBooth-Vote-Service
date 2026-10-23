package com.votingbooth.vote.core.config;

import com.votingbooth.vote.core.dataaccess.repository.SyncRunRepository;
import com.votingbooth.vote.core.model.entity.SyncRun;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// The applications live in com.votingbooth.vote.api/job, so Spring Boot's default scanning doesn't reach vote-core.
@Configuration
@EntityScan(basePackageClasses = SyncRun.class)
@EnableJpaRepositories(basePackageClasses = SyncRunRepository.class)
public class VoteCoreConfig {
}
