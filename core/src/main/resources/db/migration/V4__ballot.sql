-- The question citizens vote on, and the bridge to the scrutins representatives voted in.
CREATE TABLE ballot.ballot (
    id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('LAW_FINAL_VOTE', 'CENSURE_MOTION', 'AMENDMENT')),
    -- What the ballot is about, in the source's ids: the dossier (DLR...) for a law, the scrutin (VTAN...) otherwise.
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    opens_at TIMESTAMP NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (kind, source, external_id)
);

-- A law can be voted several times (first reading, CMP...). The decisive scrutin is the Assemblée's last vote on the whole text.
CREATE TABLE ballot.ballot_parliament_vote (
    ballot_id UUID NOT NULL REFERENCES ballot.ballot (id) ON DELETE CASCADE,
    parliament_vote_id UUID NOT NULL REFERENCES parliament.parliament_vote (id),
    is_decisive BOOLEAN NOT NULL,
    -- OPPOSITE when voting for the scrutin means voting against the ballot: a censure motion on a law passed by 49.3.
    alignment VARCHAR(10) NOT NULL CHECK (alignment IN ('SAME', 'OPPOSITE')),
    PRIMARY KEY (ballot_id, parliament_vote_id)
);

CREATE UNIQUE INDEX ballot_parliament_vote_decisive_idx ON ballot.ballot_parliament_vote (ballot_id) WHERE is_decisive;
-- The ballots a scrutin counts for.
CREATE INDEX ballot_parliament_vote_vote_idx ON ballot.ballot_parliament_vote (parliament_vote_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ballot TO vote_job;
GRANT SELECT ON ALL TABLES IN SCHEMA ballot TO vote_api;
