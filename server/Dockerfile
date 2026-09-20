# Build: docker build -t duel2048-server -f server/Dockerfile .
# Run:   docker run -p 8080:8080 duel2048-server
# Build stage: JDK 17 (Gradle 7.4.2 supports up to Java 18, not 21).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :server:installDist

# JRE 8: the server is built to Java 8 bytecode so it also runs on JRE 8 hosts.
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
