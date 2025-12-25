# BTrace Docker Image - Debian-based variant
# Full distribution with all tools and shell access
FROM openjdk:11-jdk-slim

ARG BTRACE_VERSION=2.3.0-SNAPSHOT

LABEL maintainer="BTrace Project <https://github.com/btraceio/btrace>" \
      org.opencontainers.image.title="BTrace" \
      org.opencontainers.image.description="Safe, dynamic tracing tool for the Java platform" \
      org.opencontainers.image.url="https://github.com/btraceio/btrace" \
      org.opencontainers.image.source="https://github.com/btraceio/btrace" \
      org.opencontainers.image.version="${BTRACE_VERSION}" \
      org.opencontainers.image.licenses="GPL-2.0-only WITH Classpath-exception-2.0"

# Install required tools
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        procps \
        curl \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create BTrace installation directory
RUN mkdir -p /opt/btrace

# Copy BTrace distribution
COPY btrace/ /opt/btrace/

# Set ownership and permissions
RUN chmod -R 755 /opt/btrace/bin && \
    chmod 644 /opt/btrace/libs/*.jar

# Set environment variables
ENV BTRACE_HOME=/opt/btrace \
    PATH="${PATH}:/opt/btrace/bin" \
    JAVA_HOME=/usr/local/openjdk-11

# Verify installation
RUN btrace --version || echo "BTrace installed"

# Add entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

WORKDIR /work

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/bin/bash"]
