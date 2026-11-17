# Pending btraceio/jbang-catalog fix

Found in the 3.0.0-RC1 pre-release check (2026-11-17) against `btraceio/jbang-catalog` @ `b67df6d`.
Not pushed from that session, which could not write to the catalog repository.

## Problem

`btrace.java` and `btrace-latest.java` (the `btrace@btraceio` and `btrace-latest@btraceio` aliases)
declare `//DEPS io.btrace:btrace:3.0.0-SNAPSHOT` and no `//REPOS`. JBang resolves from Maven Central
by default, and Central serves no `io.btrace:btrace` artifact at all, so the README's
"Get Started in 30 Seconds" command (`jbang btrace@btraceio ...`) cannot resolve until 3.0.0 is
released.

## Fix

`jbang-catalog.patch` (next to this file) adds this line before `//DEPS` in both scripts:

```java
//REPOS mavencentral,central-snapshots=https://central.sonatype.com/repository/maven-snapshots/
```

- Any `//REPOS` line replaces JBang's default Maven Central lookup, so Central is listed explicitly
  ("If you add any `//REPOS` lines `jbang` will no longer consult `mavencentral`",
  https://www.jbang.dev/documentation/jbang/latest/dependencies.html).
- The same line is what the agent-plugins MCP server declares
  (`plugins/btrace-observability/mcp/src/main/java/io/btrace/mcp/BTraceMcpServer.java:1`).
- The snapshot repository serves `io.btrace:btrace:3.0.0-SNAPSHOT`: build
  `3.0.0-20261116.074521-309` was fetched and its `io.btrace.boot.Loader --version` printed
  `BTrace v.3.0.0-SNAPSHOT (8f919622...)` on JDK 25 and 21. JBang itself resolving the alias was
  not run (no JBang in that session).
- The snapshot is built from `develop`, so it carries the RC1 fixes only once they are merged there
  and CI has republished it.

```bash
git clone https://github.com/btraceio/jbang-catalog && cd jbang-catalog
git apply /path/to/btrace/internal/launch/3.0.0/jbang-catalog.patch
```

## After releases

The release workflow's `update-jbang-catalog` job (`.github/workflows/release.yml`) points
`btrace.java` at each final release and, on a major or minor release, moves `btrace-latest.java` to
the next develop snapshot, adding the `//REPOS` line if it is missing. Release candidates leave the
catalog unchanged.
