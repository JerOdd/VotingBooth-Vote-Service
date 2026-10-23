package com.votingbooth.vote.api.dataaccess.client.law;

import com.votingbooth.vote.api.model.law.Law;
import com.votingbooth.vote.api.model.law.LawResponse;
import com.votingbooth.vote.api.model.law.LawStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LawHttpClient implements LawClient {

    @Override
    public LawResponse getLaw(UUID lawId) {
        return new LawResponse(
                new Law(
                        lawId,
                        LawStatus.VOTING
                )
        );
    }
}
