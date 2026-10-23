package com.votingbooth.vote.job.dataaccess.client.law;

import com.votingbooth.vote.job.model.law.LawVoteLink;

import java.util.List;

public interface LawVoteLinkClient {

    List<LawVoteLink> findAll();

}
