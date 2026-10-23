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
