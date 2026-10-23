-- Citizens' votes are political opinions (GDPR art. 9): never copied to S3 or logs, deleted on request, and published only
-- as aggregates above a minimum count. Written by the API only; the job has no access to this schema.
CREATE TABLE citizen.user_vote (
    id UUID NOT NULL,
    user_external_id VARCHAR(100) NOT NULL, -- the subject of the auth token
    ballot_id UUID NOT NULL REFERENCES ballot.ballot (id),
    choice VARCHAR(10) NOT NULL CHECK (choice IN ('FOR', 'AGAINST', 'ABSTAIN')),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_external_id, ballot_id)
);

-- Kept up to date with user_vote in the same transaction, so results are never counted live.
CREATE TABLE citizen.ballot_user_tally (
    ballot_id UUID NOT NULL REFERENCES ballot.ballot (id),
    count_for BIGINT NOT NULL DEFAULT 0,
    count_against BIGINT NOT NULL DEFAULT 0,
    count_abstain BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (ballot_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA citizen TO vote_api;
