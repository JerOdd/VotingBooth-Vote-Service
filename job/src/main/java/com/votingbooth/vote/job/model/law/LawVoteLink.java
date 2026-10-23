package com.votingbooth.vote.job.model.law;

// A scrutin the law service links to a dossier (its law_vote_link, from the dossier's voteRefs). Ids are the sources' own:
// the vote service never sees the law service's internal ids.
public record LawVoteLink(String lawSource, String lawExternalId, String lawTitle, String voteSource,
                          String voteExternalId, Kind kind, boolean decisive) {

    public enum Kind {
        TEXT,
        MOTION
    }

}
