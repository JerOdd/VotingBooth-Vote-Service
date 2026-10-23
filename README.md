# vote-service
Vote Service for VotingBooth.

- `core`: Flyway migrations, entities and repositories shared by the API and the job.
- `api` (`vote-api`): citizens' votes.
- `job` (`vote-job`): nightly sync of the Assemblée nationale's scrutins (representatives' votes) and censure motion ballots.

The database has three schemas: `parliament` (written by the job only), `ballot` (written by the job, read by the API) and
`citizen` (written by the API only; the job's role has no access to it).

## Run the job locally

```
docker compose up -d
./mvnw -pl job -am package -DskipTests
java -jar job/target/vote-job-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

It runs once and exits; `--force` reprocesses every scrutin, `--assemblee-nationale.legislatures=17` limits it to one
legislature. Set `vote.sync.schedule.enabled=true` to keep it up and sync on `vote.sync.cron` (Paris time).

Docker images: `docker build -f api/Dockerfile .` and `docker build -f job/Dockerfile .` from the repo root.
