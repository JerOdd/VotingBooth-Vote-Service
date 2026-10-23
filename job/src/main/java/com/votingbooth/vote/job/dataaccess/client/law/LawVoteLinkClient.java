package com.votingbooth.vote.job.dataaccess.client.law;

import com.votingbooth.vote.job.model.law.LawVoteLink;

import java.util.List;

// Where law ballots (LAW_FINAL_VOTE) will come from: one ballot per dossier, linked to its scrutins, the decisive one being
// the Assemblée's last vote on the whole text. A MOTION link (censure motion on a 49.3) counts with alignment OPPOSITE.
// Not implemented yet: the law service doesn't expose its links.
public interface LawVoteLinkClient {

    List<LawVoteLink> findLinks(String voteSource, int legislature);

}
