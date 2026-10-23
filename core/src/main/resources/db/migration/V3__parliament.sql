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
