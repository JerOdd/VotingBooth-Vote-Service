package com.votingbooth.vote.job.service;

import com.votingbooth.vote.core.model.entity.SyncTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// Long-running mode (vote.sync.schedule.enabled=true): syncs on the cron, in Paris time. Without it, the job runs once and exits.
@Service
@ConditionalOnProperty(name = "vote.sync.schedule.enabled", havingValue = "true")
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final ParliamentSyncService parliamentSyncService;

    public SyncScheduler(ParliamentSyncService parliamentSyncService) {
        this.parliamentSyncService = parliamentSyncService;
    }

    // A failed run is already recorded on sync_run; the scheduler keeps going and retries on the next tick.
    @Scheduled(cron = "${vote.sync.cron}", zone = "${vote.sync.zone}")
    public void sync() {
        try {
            parliamentSyncService.sync(SyncTrigger.SCHEDULED, false);
        } catch (RuntimeException e) {
            log.error("Scheduled sync failed", e);
        }
    }

}
