# BTrace Docker Images

Official Docker images for [BTrace](https://github.com/btraceio/btrace) - a safe, dynamic tracing tool for the Java platform.

## Quick Start

```dockerfile
# Copy BTrace into your application image
FROM btrace/btrace:2.3.0 AS btrace
FROM bellsoft/liberica-openjdk-debian:11-cds

COPY --from=btrace /opt/btrace /opt/btrace
ENV BTRACE_HOME=/opt/btrace PATH="${PATH}:/opt/btrace/bin"

# Your application...
```

## Image Variants

### `btrace/btrace:latest` (~25MB)
**Debian-based** - Full distribution with all tools and shell access

- **Base:** OpenJDK 11 JDK on Debian Slim
- **Use case:** General purpose, development, interactive debugging
- **Includes:** Complete BTrace toolchain, shell access, samples
- **Best for:** Development environments, interactive troubleshooting

```dockerfile
FROM btrace/btrace:2.3.0
```

### `btrace/btrace:latest-alpine` (~15MB)
**Alpine-based** - Smaller footprint for cloud environments

- **Base:** Alpine Linux with OpenJDK 11
- **Use case:** Kubernetes sidecars, resource-constrained environments
- **Includes:** Complete BTrace toolchain, smaller base OS
- **Best for:** Production sidecars, cloud deployments

```dockerfile
FROM btrace/btrace:2.3.0-alpine
```

### `btrace/btrace:latest-distroless` (~10MB)
**Distroless** - Minimal attack surface for security-focused deployments

- **Base:** Google Distroless Java 11
- **Use case:** Production agent mode, minimal security surface
- **Includes:** BTrace runtime JARs only (no shell, no scripts)
- **Best for:** Production applications using `-javaagent`

```dockerfile
FROM btrace/btrace:2.3.0-distroless AS btrace
FROM gcr.io/distroless/java11
COPY --from=btrace /opt/btrace/libs /opt/btrace/libs
```

## Usage Patterns

### Pattern 1: Multi-Stage Build

Most common - copy BTrace into your application image:

```dockerfile
FROM btrace/btrace:2.3.0 AS btrace
FROM bellsoft/liberica-openjdk-debian:11-cds
WORKDIR /app

COPY target/myapp.jar /app/
COPY --from=btrace /opt/btrace /opt/btrace

ENV BTRACE_HOME=/opt/btrace
ENV PATH="${PATH}:${BTRACE_HOME}/bin"

ENTRYPOINT ["java", "-jar", "myapp.jar"]
```

**Usage:**
```bash
docker build -t myapp:latest .
docker run -d --name myapp myapp:latest
docker exec myapp btrace <PID> /scripts/trace.btrace
```

### Pattern 2: Kubernetes Sidecar

Run BTrace as a sidecar container:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: myapp-with-btrace
spec:
  shareProcessNamespace: true  # Required!
  containers:
  - name: myapp
    image: myapp:latest

  - name: btrace-sidecar
    image: btrace/btrace:2.3.0-alpine
    command: ["/bin/sh", "-c", "while true; do sleep 30; done"]
    volumeMounts:
    - name: btrace-scripts
      mountPath: /scripts

  volumes:
  - name: btrace-scripts
    configMap:
      name: btrace-scripts
```

**Usage:**
```bash
kubectl exec myapp-with-btrace -c btrace-sidecar -- \
  btrace $(pgrep -f myapp) /scripts/trace.btrace
```

### Pattern 3: Development Image

Extend BTrace image for development:

```dockerfile
FROM btrace/btrace:2.3.0-alpine

COPY target/myapp.jar /app/myapp.jar
COPY scripts/*.btrace /scripts/

ENTRYPOINT ["btracer"]
CMD ["-v", "-o", "/tmp/btrace-output.txt", "/app/myapp.jar"]
```

### Pattern 4: Distroless Production

Minimal image with BTrace agent:

```dockerfile
FROM btrace/btrace:2.3.0-distroless AS btrace
FROM gcr.io/distroless/java11
WORKDIR /app

COPY --from=build /app/target/myapp.jar /app/
COPY --from=btrace /opt/btrace/libs /opt/btrace/libs

ENTRYPOINT ["java", \
  "-javaagent:/opt/btrace/libs/btrace-agent.jar=script=/scripts/trace.btrace", \
  "-Xbootclasspath/a:/opt/btrace/libs/btrace-boot.jar", \
  "-jar", "/app/myapp.jar"]
```

### Pattern 5: Docker Compose

Local development with BTrace:

```yaml
version: '3.8'
services:
  myapp:
    image: myapp:latest
    ports:
      - "8080:8080"

  btrace:
    image: btrace/btrace:2.3.0-alpine
    network_mode: "service:myapp"
    pid: "service:myapp"
    volumes:
      - ./scripts:/scripts
      - ./output:/output
    command: >
      sh -c 'sleep 5; btrace $(pgrep -f myapp) /scripts/trace.btrace'
```

## Image Selection Guide

| Variant | Size | Shell | Scripts | Use Case |
|---------|------|-------|---------|----------|
| **latest** | ~25MB | ✓ | ✓ | Development, debugging |
| **alpine** | ~15MB | ✓ | ✓ | Kubernetes sidecar |
| **distroless** | ~10MB | ✗ | ✗ | Production agent mode |

**Recommendations:**
- **Development:** Use `btrace:latest` for full tooling
- **Kubernetes Sidecar:** Use `btrace:latest-alpine` for size efficiency
- **Production Agent:** Use `btrace:latest-distroless` for security

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BTRACE_HOME` | `/opt/btrace` | BTrace installation directory |
| `BTRACE_OPTS` | (empty) | Additional BTrace command options |
| `JAVA_HOME` | (auto-detected) | Java installation path |
| `BTRACE_VERBOSE` | `false` | Print version info on startup |

## Scenarios

### Troubleshoot Production Issue

```bash
# Copy script into container
docker cp troubleshoot.btrace myapp:/tmp/

# Find Java process PID
docker exec myapp jps -l

# Attach BTrace
docker exec myapp btrace <PID> /tmp/troubleshoot.btrace

# View output
docker exec myapp cat /tmp/btrace-output.txt
```

### Continuous Monitoring in Kubernetes

```bash
# Deploy scripts as ConfigMap
kubectl create configmap btrace-scripts \
  --from-file=monitor.btrace=./scripts/monitor.btrace

# Deploy application with sidecar
kubectl apply -f app-with-btrace-sidecar.yaml

# Stream output
kubectl logs -f myapp-pod -c btrace-sidecar

# Update script without restart
kubectl create configmap btrace-scripts \
  --from-file=monitor.btrace=./scripts/updated.btrace \
  --dry-run=client -o yaml | kubectl apply -f -
```

### Performance Profiling

```dockerfile
FROM btrace/btrace:2.3.0 AS btrace
FROM bellsoft/liberica-openjdk-debian:11-cds

COPY --from=btrace /opt/btrace /opt/btrace
ENV BTRACE_HOME=/opt/btrace PATH="${PATH}:${BTRACE_HOME}/bin"

COPY target/myapp.jar /app/
COPY scripts/profile-*.btrace /scripts/

ENTRYPOINT ["btracer", \
  "-v", "-o", "/tmp/profile.txt", \
  "/scripts/profile-methods.btrace", \
  "-jar", "/app/myapp.jar"]
```

## Best Practices

1. **Use specific version tags in production:**
   ```dockerfile
   FROM btrace/btrace:2.3.0  # Good
   FROM btrace/btrace:latest  # Avoid in production
   ```

2. **Multi-stage builds minimize size:**
   ```dockerfile
   FROM btrace/btrace:2.3.0 AS btrace
   FROM your-app-image
   COPY --from=btrace /opt/btrace /opt/btrace
   ```

3. **Enable process namespace sharing in Kubernetes:**
   ```yaml
   spec:
     shareProcessNamespace: true  # Required!
   ```

4. **Store scripts in ConfigMaps:**
   ```bash
   kubectl create configmap btrace-scripts --from-file=./scripts/
   ```

5. **Use appropriate security contexts:**
   ```yaml
   securityContext:
     runAsNonRoot: true
     readOnlyRootFilesystem: true
     capabilities:
       drop: ["ALL"]
       add: ["SYS_PTRACE"]  # Required
   ```

6. **Monitor BTrace overhead:**
   - Start with minimal instrumentation
   - Test in staging first
   - Use sampling for high-frequency events

## Troubleshooting

### "Cannot attach to process"
```bash
# Ensure process namespace sharing
docker run --pid=container:target-container btrace/btrace:latest

# Kubernetes: verify shareProcessNamespace
kubectl get pod myapp -o yaml | grep shareProcessNamespace
```

### "BTRACE_HOME not found"
```bash
# Verify installation
docker exec myapp ls -la $BTRACE_HOME
docker exec myapp env | grep BTRACE
```

### "Java tools not available"
```bash
# Ensure JDK (not JRE) is installed
docker exec myapp java -version
docker exec myapp which javac

# Java 9+ requires module exports
ENV JAVA_OPTS="--add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
```

## Building Images Locally

```bash
# Build BTrace distribution first
./gradlew :btrace-dist:build

# Build Docker images
./gradlew :btrace-dist:buildDockerImages

# Or build manually
VERSION=2.3.0-SNAPSHOT  # Set this to your BTrace version
cd docker
docker build -t btrace/btrace:local -f Dockerfile ../btrace-dist/build/resources/main/v${VERSION}
docker build -t btrace/btrace:local-alpine -f Dockerfile.alpine ../btrace-dist/build/resources/main/v${VERSION}
docker build -t btrace/btrace:local-distroless -f Dockerfile.distroless ../btrace-dist/build/resources/main/v${VERSION}
```

## Supported Platforms

- **linux/amd64** - Intel/AMD 64-bit (all variants)
- **linux/arm64** - ARM 64-bit (all variants)

Note: Native DTrace library (`libbtrace.so`) is x86-only. DTrace features are not available on ARM platforms.

## License

BTrace is licensed under GPL-2.0-only WITH Classpath-exception-2.0.

## Links

- **GitHub:** https://github.com/btraceio/btrace
- **Documentation:** https://github.com/btraceio/btrace/tree/develop/docs
- **Issues:** https://github.com/btraceio/btrace/issues
- **Docker Hub:** https://hub.docker.com/r/btrace/btrace

## Support

For questions and support:
- GitHub Issues: https://github.com/btraceio/btrace/issues
- Documentation: https://github.com/btraceio/btrace/tree/develop/docs
