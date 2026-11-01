# BTrace Docker Image - Debian-based variant
# Full distribution with all tools and shell access
FROM bellsoft/liberica-openjdk-debian:11.0.32.1-cds

ARG BTRACE_VERSION=3.0.0-SNAPSHOT

LABEL maintainer="BTrace Project <https://github.com/btraceio/btrace>" \
      org.opencontainers.image.title="BTrace" \
      org.opencontainers.image.description="Safe, dynamic tracing tool for the Java platform" \
      org.opencontainers.image.url="https://github.com/btraceio/btrace" \
      org.opencontainers.image.source="https://github.com/btraceio/btrace" \
      org.opencontainers.image.version="${BTRACE_VERSION}" \
      org.opencontainers.image.licenses="Apache-2.0"

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

# Set environment variables. JAVA_HOME is inherited from the base image, which
# points it at the JDK for the image's own architecture
# (/usr/lib/jvm/jdk-<version>-bellsoft-<arch>); overriding it here breaks the
# launcher scripts.
ENV BTRACE_HOME=/opt/btrace \
    PATH="${PATH}:/opt/btrace/bin"

# Verify installation: the launchers must actually run, or the build fails
RUN test -x "${JAVA_HOME}/bin/java" && \
    btrace --version && \
    btracec --version

# Add entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

WORKDIR /work

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/bin/bash"]
