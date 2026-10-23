CREATE TABLE votes (
    value       INTEGER NOT NULL,
    user_id     UUID    NOT NULL,
    law_id      UUID    NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, law_id)
);

-- The primary key is (user_id, law_id), which can't serve lookups by law_id alone.
CREATE INDEX idx_votes_law_id_value ON votes (law_id, value);

-- Representatives' votes and citizens' votes never share write access:
--   parliament  scrutins and representatives' votes, written only by the job
--   ballot      the questions citizens and representatives both vote on, written only by the job
--   citizen     citizens' votes, political opinions (GDPR art. 9), written only by the API
CREATE SCHEMA parliament;
CREATE SCHEMA ballot;
CREATE SCHEMA citizen;

-- Group roles without login. The infrastructure creates the login users as members of them (locally:
-- the postgres-init service of docker-compose.yml), and Flyway runs as the database owner. Created here if missing so the grants never fail.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'vote_job') THEN
        CREATE ROLE vote_job NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'vote_api') THEN
        CREATE ROLE vote_api NOLOGIN;
    END IF;
END
$$;

-- The job gets nothing on citizen: not even USAGE, so it can't see the tables exist.
GRANT USAGE ON SCHEMA parliament, ballot TO vote_job, vote_api;
GRANT USAGE ON SCHEMA citizen TO vote_api;

-- The API's existing table, until it moves to citizen.user_vote.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.votes TO vote_api;

-- One row per archive (legislature) and per run of the job.
CREATE TABLE parliament.sync_run (
    id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    trigger VARCHAR(20) NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    legislature INTEGER NOT NULL,
    archive_url VARCHAR(500) NOT NULL,
    archive_sha256 VARCHAR(64),
    -- The Assemblée's servers don't all serve the latest archive: one older than the last applied run is skipped.
    archive_last_modified TIMESTAMP,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SKIPPED', 'SUCCEEDED', 'FAILED')),
    created INTEGER NOT NULL DEFAULT 0,
    updated INTEGER NOT NULL DEFAULT 0,
    unchanged INTEGER NOT NULL DEFAULT 0,
    ignored INTEGER NOT NULL DEFAULT 0, -- files of another chamber, e.g. the Congrès (VTCG...) in the L16 archive
    failed INTEGER NOT NULL DEFAULT 0,
    error TEXT,
    PRIMARY KEY (id)
);

-- A scrutin. Counts are the official ones (syntheseVote), never recomputed: a few L16 scrutins differ from the
-- nominative lists by one vote.
CREATE TABLE parliament.parliament_vote (
    id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(50) NOT NULL, -- VTANR5L17V4745
    jurisdiction_code VARCHAR(2) NOT NULL,
    chamber_code VARCHAR(2) NOT NULL,
    legislature INTEGER NOT NULL,
    number INTEGER NOT NULL,
    vote_date DATE NOT NULL, -- the source has no time
    session_ref VARCHAR(50) NOT NULL,
    sitting_ref VARCHAR(50) NOT NULL,
    vote_type VARCHAR(3) NOT NULL CHECK (vote_type IN ('SPO', 'SPS', 'MOC')),
    result VARCHAR(10) NOT NULL CHECK (result IN ('ADOPTED', 'REJECTED')),
    title TEXT NOT NULL,
    normalized_title TEXT NOT NULL, -- lower case, one kind of apostrophe and space: what title matching works on
    -- Derived from the title, for display and filtering only: ballots never rely on it.
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('FINAL_TEXT', 'PART', 'ARTICLE', 'AMENDMENT', 'MOTION', 'PROCEDURAL', 'OTHER')),
    requester TEXT,
    -- objet.dossierLegislatif.dossierRef, filled since April 2025 only. The law service's links remain the reference.
    dossier_external_id VARCHAR(50),
    voters INTEGER NOT NULL,
    expressed INTEGER NOT NULL,
    required_majority INTEGER NOT NULL,
    count_for INTEGER NOT NULL,
    count_against INTEGER NOT NULL, -- always 0 on a censure motion: only the votes for it are cast
    count_abstain INTEGER NOT NULL,
    count_non_voting INTEGER NOT NULL,
    raw_s3_key VARCHAR(500) NOT NULL,
    raw_s3_version_id VARCHAR(255),
    raw_sha256 VARCHAR(64) NOT NULL, -- decides whether the next run reprocesses the file
    first_seen_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (source, external_id)
);

CREATE INDEX parliament_vote_dossier_idx ON parliament.parliament_vote (source, dossier_external_id);

-- How each political group voted, as the scrutin reports it.
CREATE TABLE parliament.group_vote (
    parliament_vote_id UUID NOT NULL REFERENCES parliament.parliament_vote (id) ON DELETE CASCADE,
    group_external_id VARCHAR(50) NOT NULL, -- PO..., PO0 for representatives without a group
    group_size INTEGER NOT NULL,
    -- Null on a censure motion: the source says "pour" for every group, as only the votes for it are listed.
    majority_position VARCHAR(10) CHECK (majority_position IN ('FOR', 'AGAINST', 'ABSTAIN')),
    count_for INTEGER NOT NULL,
    count_against INTEGER NOT NULL,
    count_abstain INTEGER NOT NULL,
    count_non_voting INTEGER NOT NULL,
    PRIMARY KEY (parliament_vote_id, group_external_id)
);

-- One row per representative listed in the scrutin, with their mandate, person and group at the time of the vote, so the
-- affinity never needs the representative service. The refs are kept as they are, even when that service doesn't know them.
CREATE TABLE parliament.representative_vote (
    parliament_vote_id UUID NOT NULL REFERENCES parliament.parliament_vote (id) ON DELETE CASCADE,
    mandate_external_id VARCHAR(50) NOT NULL, -- the seat mandate (PM...), not the group membership
    person_external_id VARCHAR(50) NOT NULL,  -- PA...
    -- PO...; null when the scrutin doesn't tell groups apart (12 L17 scrutins of 2 December 2024 list every group as PO0).
    group_external_id VARCHAR(50),
    position VARCHAR(10) NOT NULL CHECK (position IN ('FOR', 'AGAINST', 'ABSTAIN', 'NON_VOTING')),
    by_delegation BOOLEAN NOT NULL,
    -- Why a NON_VOTING representative couldn't vote; null when they simply didn't.
    non_voting_reason VARCHAR(20) CHECK (non_voting_reason IN ('GOVERNMENT_MEMBER', 'ASSEMBLY_PRESIDENT', 'SITTING_PRESIDENT')),
    PRIMARY KEY (parliament_vote_id, mandate_external_id)
);

-- A representative's voting record.
CREATE INDEX representative_vote_person_idx ON parliament.representative_vote (person_external_id, parliament_vote_id);

-- Mises au point: what a representative says afterwards they meant to vote. They don't change the official result, and a
-- quarter of them come from representatives who aren't in the scrutin at all, so they can't be a column of representative_vote.
CREATE TABLE parliament.representative_vote_correction (
    parliament_vote_id UUID NOT NULL REFERENCES parliament.parliament_vote (id) ON DELETE CASCADE,
    mandate_external_id VARCHAR(50) NOT NULL,
    person_external_id VARCHAR(50) NOT NULL,
    declared_position VARCHAR(10) NOT NULL CHECK (declared_position IN ('FOR', 'AGAINST', 'ABSTAIN', 'NON_VOTING')),
    is_malfunction BOOLEAN NOT NULL, -- reported as a voting machine malfunction (miseAuPoint.dysfonctionnement)
    PRIMARY KEY (parliament_vote_id, mandate_external_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA parliament TO vote_job;
GRANT SELECT ON ALL TABLES IN SCHEMA parliament TO vote_api;

-- The question citizens vote on, and the bridge to the scrutins representatives voted in.
CREATE TABLE ballot.ballot (
    id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('LAW_FINAL_VOTE', 'CENSURE_MOTION', 'AMENDMENT')),
    -- What the ballot is about: a law, by the law service's id (its title stays there), or a scrutin, by its source's id.
    law_id UUID,
    source VARCHAR(50),
    external_id VARCHAR(50),
    title TEXT,
    opens_at TIMESTAMP NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (kind, source, external_id),
    CHECK ((kind = 'LAW_FINAL_VOTE' AND law_id IS NOT NULL)
        OR (kind <> 'LAW_FINAL_VOTE' AND source IS NOT NULL AND external_id IS NOT NULL AND title IS NOT NULL))
);

-- One ballot per law, found by its id when the law's page asks for its results.
CREATE UNIQUE INDEX ballot_law_idx ON ballot.ballot (law_id) WHERE kind = 'LAW_FINAL_VOTE';

-- A law can be voted several times (first reading, CMP...). The decisive scrutin is the Assemblée's last vote on the whole text,
-- or for a law passed by 49.3 (no such vote) its last censure motion, with alignment OPPOSITE.
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
