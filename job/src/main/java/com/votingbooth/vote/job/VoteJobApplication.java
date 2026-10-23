package com.votingbooth.vote.job;

import com.votingbooth.vote.core.config.VoteCoreConfig;
import com.votingbooth.vote.core.model.entity.SyncTrigger;
import com.votingbooth.vote.job.service.ParliamentSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// By default, runs one sync and exits (Kubernetes CronJob); a failed run exits with a non-zero code.
//   --manual  records the run as MANUAL instead of SCHEDULED
//   --force   reprocesses every scrutin, ignoring the stored hashes (implies --manual)
// With vote.sync.schedule.enabled=true it stays up and syncs on vote.sync.cron instead (see SyncScheduler).
@SpringBootApplication
@Import(VoteCoreConfig.class)
public class VoteJobApplication implements ApplicationRunner {

	private final ParliamentSyncService parliamentSyncService;
	private final boolean scheduled;

	public VoteJobApplication(ParliamentSyncService parliamentSyncService,
							  @Value("${vote.sync.schedule.enabled:false}") boolean scheduled) {
		this.parliamentSyncService = parliamentSyncService;
		this.scheduled = scheduled;
	}

	public static void main(String[] args) {
		SpringApplication.run(VoteJobApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (scheduled) {
			return;
		}
		boolean force = args.containsOption("force");
		SyncTrigger trigger = force || args.containsOption("manual") ? SyncTrigger.MANUAL : SyncTrigger.SCHEDULED;
		parliamentSyncService.sync(trigger, force);
	}

}
