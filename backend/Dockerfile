# Stage 1: Build with Maven wrapper
# Uses eclipse-temurin:21-jdk to match the project's Java 21 requirement
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache dependency resolution layer — copy build descriptors first
COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# Build application (skip tests — tested on host before image build)
COPY src src
RUN ./mvnw package -DskipTests -B -q

# Extract Spring Boot layered jar for better cache utilization
# layertools extract (Spring Boot 3.x) outputs layers into /workspace directly:
#   dependencies/, spring-boot-loader/, snapshot-dependencies/, application/
RUN java -Djarmode=layertools -jar target/*.jar extract

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — JRE only (200–300 MB smaller than JDK image)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy Spring Boot layered jar dirs in ascending change-frequency order
# (dependencies change least often → application code changes most often)
COPY --from=build /workspace/dependencies/ ./
COPY --from=build /workspace/spring-boot-loader/ ./
COPY --from=build /workspace/snapshot-dependencies/ ./
COPY --from=build /workspace/application/ ./

# Run as non-root for security
RUN useradd -r -u 1001 -g root appuser
USER appuser

EXPOSE 8080

# Health check — Docker polls this; compose backend waits until healthy before frontend starts
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Spring Boot 3 uses JarLauncher from org.springframework.boot.loader.launch
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
