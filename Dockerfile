# ---------- build stage: compile the jar with the JDK ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/seatlock-*.jar /src/app.jar

# ---------- run stage: small JRE image, non-root user ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 seatlock

# Small-footprint JVM flags: fit comfortably in 512 MB (free hosting plans) and start faster on
# slow CPUs (C1 compiler only). Override JAVA_OPTS to tune for bigger machines.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m"

COPY --from=build /src/app.jar app.jar

# Class Data Sharing (CDS): start the app once at build time, up to the point where the Spring
# context is ready, and record every loaded class in app.jsa. Later starts map that archive
# instead of loading classes one by one: about 2x faster startup on a 0.1-CPU free instance
# (measured 150 s -> 67 s). No database or Redis is needed for this training run.
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && cd extracted \
 && java $JAVA_OPTS -XX:ArchiveClassesAtExit=app.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=demo \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar \
 && rm ../app.jar \
 && chown -R seatlock /app

USER seatlock
WORKDIR /app/extracted
EXPOSE 8080
# If JAVA_OPTS changes in a way the archive can't support, the JVM just starts without CDS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=app.jsa -jar app.jar"]
