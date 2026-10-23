package com.votingbooth.vote.job.dataaccess.client.law;

import com.votingbooth.vote.job.model.law.LawVoteLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class LawVoteLinkHttpClient implements LawVoteLinkClient {

    private final RestClient restClient;

    public LawVoteLinkHttpClient(RestClient.Builder restClientBuilder, @Value("${clients.law.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<LawVoteLink> findAll() {
        return restClient.get()
                .uri("/vote-links")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

}
