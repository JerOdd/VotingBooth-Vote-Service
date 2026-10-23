package com.votingbooth.vote.job.model.law;

import java.util.UUID;

// law-api's GET /vote-links: a scrutin a law was voted in, the law by the law service's id. decisive marks the Assemblée's
// last vote on the whole text; a law passed by 49.3 has none, only MOTION links (its censure motions).
public record LawVoteLink(UUID lawId, String source, String scrutinExternalId, Kind kind, boolean decisive) {

    public enum Kind {
        TEXT,
        MOTION
    }

}
