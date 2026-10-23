# Build from the repo root so the parent POM and core are available:
#   docker build -f job/Dockerfile -t vote-job .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY core/pom.xml core/
COPY api/pom.xml api/
COPY job/pom.xml job/
# vote-core is built in this reactor, not downloaded, so leave our own group out of go-offline
RUN mvn -B -pl job -am dependency:go-offline -DexcludeGroupIds=com.votingbooth

COPY core/src core/src
COPY job/src job/src
RUN mvn -B -pl job -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/job/target/vote-job-*.jar app.jar
CMD ["java", "-jar", "app.jar"]
