package com.votingbooth.vote.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Timestamps are stored in UTC; services take the clock as a dependency so tests can fix it.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
