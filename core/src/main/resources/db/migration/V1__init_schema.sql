CREATE TABLE votes (
    value       INTEGER NOT NULL,
    user_id     UUID    NOT NULL,
    law_id      UUID    NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, law_id)
);

-- The primary key is (user_id, law_id), which can't serve lookups by law_id alone.
CREATE INDEX idx_votes_law_id_value ON votes (law_id, value);
