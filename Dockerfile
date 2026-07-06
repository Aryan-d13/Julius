# Stage 1: Build Java Application
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /app
COPY pom.xml .
# Cache maven dependencies
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create Runtime Environment
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install system dependencies: Python 3, virtualenv, FFmpeg
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create virtual environment for Whisper dependencies
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install PyTorch (CPU-only version to save space) and Whisper packages
RUN pip3 install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu
RUN pip3 install --no-cache-dir faster-whisper==1.0.3 requests yt-dlp

# Copy compiled backend jar
COPY --from=builder /app/target/clipper-1.0.0-SNAPSHOT.jar /app/clipper.jar

# Setup runtime environment variables & properties configuration path
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=live", "-Dclipper.python.path=/opt/venv/bin/python", "-Dclipper.whisper.model=tiny", "-jar", "/app/clipper.jar"]
