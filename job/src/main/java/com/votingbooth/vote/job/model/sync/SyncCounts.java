package com.votingbooth.vote.job.model.sync;

// Scrutins counted during a run, reported on sync_run, plus the representatives' votes written (for the logs).
public class SyncCounts {

    private int created;
    private int updated;
    private int unchanged;
    private int ignored;
    private int failed;
    private long representativeVotes;

    public void created(int representativeVotes) {
        created++;
        this.representativeVotes += representativeVotes;
    }

    public void updated(int representativeVotes) {
        updated++;
        this.representativeVotes += representativeVotes;
    }

    public void unchanged() {
        unchanged++;
    }

    public void ignored() {
        ignored++;
    }

    public void failed() {
        failed++;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public int getIgnored() {
        return ignored;
    }

    public int getFailed() {
        return failed;
    }

    public long getRepresentativeVotes() {
        return representativeVotes;
    }
}
