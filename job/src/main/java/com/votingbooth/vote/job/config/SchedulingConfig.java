package com.votingbooth.vote.job.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Only in long-running mode: the scheduler's threads would otherwise keep a run-once job from exiting.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vote.sync.schedule.enabled", havingValue = "true")
public class SchedulingConfig {
}
