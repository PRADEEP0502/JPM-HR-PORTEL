# JPM HR Portal backend - container image (used by Render; works on any Docker host).
# Build:  docker build -t jpm-hr-portal-api backend/
FROM eclipse-temurin:25-jdk AS build
# The Maven wrapper downloads Maven with curl.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
# Run as an unprivileged user.
RUN useradd --system --uid 10001 --home /app app
WORKDIR /app
COPY --from=build /app/target/hrms-backend-1.0.0.jar app.jar
USER app
# Fits small instances (e.g. 512 MB): heap is a share of the container memory, small thread stacks.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
# Render (and most hosts) pass the port in $PORT.
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
