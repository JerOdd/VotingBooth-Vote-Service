# Build from the repo root so the parent POM and core are available:
#   docker build -f api/Dockerfile -t vote-api .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY core/pom.xml core/
COPY api/pom.xml api/
COPY job/pom.xml job/
# vote-core is built in this reactor, not downloaded, so leave our own group out of go-offline
RUN mvn -B -pl api -am dependency:go-offline -DexcludeGroupIds=com.votingbooth

COPY core/src core/src
COPY api/src api/src
RUN mvn -B -pl api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api/target/vote-api-*.jar app.jar
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
