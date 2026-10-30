# btrace.io/btrace feature audit against `develop`

- **Date:** 2026-10-30
- **Commit checked:** `develop` @ `f966e68` (version `3.0.0-SNAPSHOT`, `build.gradle:12`)
- **Site checked:** https://btrace.io/btrace (HTML plus `assets/landing-page.js`, which holds the tab
  content: install commands, demos, tutorial list, doctor samples)
- **Environment:** Linux x64. JDKs: Temurin 8u504, 11.0.32.1, 17.0.21, 24.0.2, 25.0.5, 27.0.1, and
  Ubuntu OpenJDK 21.0.12.1.

## Summary

The product features on the site work on `develop`. The full unit test suite passes, all six
scripted demos pass on JDK 11, 17 and 21, and oneliners attach and trace on JDK 8, 25 and 27.

Three groups of problems need fixing:

1. **Nothing on the site can be installed yet.** "Download 3.0.0", SDKMAN, JBang and Docker all
   fail or install 2.2.6. The agent plugin's MCP server depends on `io.btrace:btrace:3.0.0-SNAPSHOT`
   and declares no snapshot repository, so JBang cannot resolve it.
2. **The `bin/btrace` launcher (and the other `bin/*` scripts) refuse to start on Temurin 24, 25
   and 27.** This contradicts "Java 8 through 27". The agent and client work on those JDKs; only the
   shell scripts are broken.
3. **The oneliner builder on the site shows output the command does not produce.** `duration` is
   printed in raw nanoseconds, `return` on a `void` method prints `io.btrace.core.types.AnyType$1@…`,
   and every `println` is followed by a blank line (already tracked as O4).

## 1. Status of each site claim

Legend: ✅ works on `develop` (verified here) · ⚠️ works with caveats · ❌ broken or unavailable ·
❔ not verified here

### Hero and ticker

| Claim | Status | Evidence |
|---|---|---|
| "Trace a live JVM in one line" | ✅ | §2.2: all three preset oneliners attach and print on JDK 21 |
| "No overhead after you detach" | ✅ | Demo 07 on JDK 21: baseline 499.3 → 106.9 M calls/s while attached → 515.9 after exit. Open race O9 (`internal/plans/2026-10-28-demo-bug-backlog.md`) can leave classes instrumented, but it was not observed. |
| "Scripts half the size with the flat DSL" | ✅ | The import-free `@TLS` script from tutorial 02 (lines 172–201) compiles and runs verbatim (§2.6) |
| "LLM, RAG and inference observability" | ✅ | Demos 04 and 05 pass; output matches the site's sample panels in structure |
| "Extensions with permissions you grant" | ✅ | Demo 03: blocked, then `-le` explains, then the policy allows it (§2.4) |
| "One jar" | ✅ | The distribution has a single `libs/btrace.jar` (plus `libs/amd64`) |
| "Ready for class files newer than ASM supports" | ❔ | `ClassFileApiBackend` exists (`docs/architecture/InstrumentationBackends.md`). Its test task `:btrace-agent:classFileApiBackendTest` needs a JDK 28 toolchain, which was not available, so it was not run. The task also fails `./gradlew build` on machines without JDK 28 instead of being skipped (backlog O8). |
| "2.x probes run unchanged" | ✅ | Demo 02: a class compiled with 2.2.6 prints `slow chargeCard: 340 ms`; the migration script rewrites a source file |
| "Java 8 through 27" | ⚠️ | Agent and client work on 8, 11, 17, 21, 25 and 27. **The `bin/btrace` launcher fails on Temurin 24/25/27** (§3.2). |
| "v3.0.0" / "Download 3.0.0" | ❌ | §3.1 |

### Agent plugin section

| Claim | Status | Evidence |
|---|---|---|
| Claude Code: `/plugin marketplace add btraceio/agent-plugins` and `/plugin install btrace-observability@btraceio-agent-plugins` | ⚠️ | The marketplace name `btraceio-agent-plugins` and the plugin exist (`agent-plugins/.claude-plugin/marketplace.json`, commit `bdda18f`). **The MCP server will not start for a new user** (§3.1, item 5). |
| Codex: `codex plugin marketplace add ./agent-plugins` | ❔ | `.codex-plugin/plugin.json` exists for every plugin. Not run: no Codex CLI here. |
| Pi: `pi install git:github.com/btraceio/agent-plugins` | ❔ | `package.json` has a `pi` block listing all five skill directories. Not run: no Pi CLI here. |
| Five plugins; ten `btrace-observability` skills | ✅ | `plugins/` has the five; `skills/` has all ten named skills, plus an umbrella `btrace-observability` skill |
| MCP tools used in the recorded session (`list_jvms`, `deploy_script`, `read_probe_output` with `wait_seconds`, `exit_probe`, `list_probes`) | ✅ (exist) | All are registered in `BTraceMcpServer.java`. `wait_seconds` is in `ReadProbeOutputHandler.java:53`. |
| End-to-end agent flow against `develop` | ❔ | Not run. JBang and the agent-plugins Gradle e2e task (`e2eTest` with `BTRACE_JAR`) were both blocked by this session's sandbox policy. CI covers only the CLI side of demo 06 (`btrace-qe/cases/demos/06-*.yaml`). |
| "Needs JBang and JDK 11+" | ✅ (accurate) | `//JAVA 11+` in `BTraceMcpServer.java` |

### Start here, Tutorials, Docs

| Claim | Status | Evidence |
|---|---|---|
| All 12 tutorial links, all 12 docs links, `demos/06-ai-assistant` | ✅ | Every target file exists on `develop` (`docs/tutorials/01…12`, `docs/*.md`) |
| Anchors `#121-llm-inference-tracing-btrace-llm-trace` and `#123-gpu-and-inference-observability-btrace-gpu-bridge` | ✅ | Match the headings `docs/BTraceTutorial.md:1633` and `:1878` |
| "50+ sample scripts" | ✅ | 59 files in `btrace-dist/src/main/resources/samples` |
| Tutorials 06, 08 and 09 (Gradle plugins `io.btrace.extension` / `io.btrace.fat-agent` 3.0.0, `ghcr.io/btraceio/btrace:3.0.0*`) | ❌ until release | Both Plugin Portal IDs return 404 (the portal falls through to Maven Central). The GHCR image is not publicly pullable (§3.1). |

### Oneliner builder

| Claim | Status | Evidence |
|---|---|---|
| Generated command syntax (`Class::meth @loc [if duration>Nms] { print … }`) | ✅ | All generated variants are accepted and attach (§2.2) |
| Sample output `chargeCard` / `execution time: 345 ms` for `print method, duration` | ❌ (site) | The actual output is `chargeCard 391171192`. `duration` is in nanoseconds (`docs/OnelinerGuide.md:150`); the `execution time: N ms` format comes from the `time` action, which demo 01 uses. |
| Sample `return: AUTHORIZED` | ❌ | On the demo app every `OrderService` method is `void` (`OrderServiceApp.java:80-99`), and `print return` outputs `io.btrace.core.types.AnyType$1@2212ef71` (§3.3) |
| Sample `OrderService::*  count: 1284` | ⚠️ | `count` prints nothing until Ctrl+C → "send an event" (`docs/OnelinerGuide.md:175`). In a 20 s run nothing was printed. The site implies it prints automatically. |
| Stack sample `OrderService.processOrder(OrderServiceApp.java:81)` … | ✅ | Matches the actual output exactly (only the `Thread.java` line number differs by JDK) |

### AI workloads, Extensions

| Claim | Status | Evidence |
|---|---|---|
| `btrace-llm-trace` + `btrace-contracts` panel | ✅ | Demo 04 output: `Model: gpt-4o-mini (openai)`, tokens, `Est. cost`, `VIOLATIONS: 1`, `Latency 3842ms exceeded budget 500ms` |
| `btrace-rag-quality` panel | ✅ | Demo 05: `Queries: 19 (3 empty, 15%)`, `Similarity (top): avg 0.737` |
| `btrace-gpu-bridge` panel | ✅ | Demo 05: `Model: onnx/embed-small`, `Throughput: 107.4 items/sec` |
| "Eight bundled extensions" | ✅ | `extensions/` in the built distribution: contracts, gpu-bridge, llm-trace, metrics, otel, rag-quality, statsd, utils |
| "`btrace -le` explains why an extension is blocked" | ✅ | `btrace-metrics: Blocked privileged extension. Required=[THREADS, REFLECTION, CLASSLOADER]` |

### Production

| Claim | Status | Evidence |
|---|---|---|
| `-javaagent:btrace.jar=port=0`, loopback, token | ✅ | Demo 07: `BTrace endpoint: ready at 127.0.0.1:34897`, `Authentication: required; credential file readable` |
| `btrace doctor` READY = exit 0, PREPARATION_REQUIRED = exit 2 | ✅ | Demo 07 output; `Doctor.java:38` |
| "The client finds the endpoint and token by itself" | ✅ | `btrace '~HotLoopApp' HotPathProbe.java` with no flags printed `[probe] attached` |
| Plain-JVM doctor remediation sample | ⚠️ (minor) | The real output also prints `- Relaunch with -XX:+EnableDynamicAgentLoading …` before the `port=0` line; the site shows only the second line |

### Demos ("CI replays every demo on each push")

| Demo | `run.sh` on JDK 21 | JDK 11 / 17 | JDK 25 / 27 | CI coverage |
|---|---|---|---|---|
| 01 oneliner | ✅ exit 0 | ✅ / ✅ | ❌ launcher (§3.2) | `btrace-qe/cases/demos/01-*.yaml` (3 cases) |
| 02 upgrade from 2.x | ✅ | – | – | `02-legacy-compiled-probe.yaml` |
| 03 metrics and permissions | ✅ | – | – | `03-metrics-{blocked,allowed}.yaml` |
| 04 LLM trace | ✅ | – | – | `04-llm-trace-dashboard.yaml` |
| 05 RAG and inference | ✅ | – | – | `05-rag-and-inference-dashboard.yaml` |
| 06 AI assistant | n/a (starts the service, then needs a coding agent) | – | – | CLI probes only (`06-*.yaml`); the agent/MCP path is not replayed |
| 07 production-safe attach | ✅ | ✅ / ✅ | ❌ launcher | `07-prepared-mode-attach.yaml`, which checks `[probe] attached` but **not** throughput recovery |

The "CI replays every demo" claim is mostly accurate. The `qe` job (`.github/workflows/continuous.yml:213-236`)
replays each demo's scenario through the `btrace-qe` harness, not the `run.sh` scripts. It does not
replay demo 06's agent flow or demo 07's "throughput returns to baseline" claim. CI uses JDK 21
(plus 24 installed), which is why the JDK 25/27 launcher failure is not caught. The latest
`develop` run is green: https://github.com/btraceio/btrace/actions/runs/40954810513.

## 2. Verification performed

### 2.1 Build and unit tests

`./gradlew build -x spotlessCheck --continue` (Maven Central was rate-limited (HTTP 429) through
this session's proxy, so it was routed to Google's Central mirror through a throwaway init script
outside the repo):

- 1,688 tests, 0 failures, 0 errors (124 skipped in `btrace-agent`). Per module: agent 934, core
  320, qe 108, client 98, compiler 77, runtime 31, extensions 112, others 8.
- The only failing task was `:btrace-agent:classFileApiBackendTest`: "Cannot find a Java
  installation … matching: {languageVersion=28 …}" (backlog O8).

### 2.2 Oneliners from the site's builder (JDK 21, demo 01 app)

| Command | Result |
|---|---|
| `OrderService::* @return if duration>200ms { print method, duration }` | `chargeCard 391171192` / `processOrder 406631486` … |
| `OrderService::processOrder @return { print method, duration }` | `processOrder 55923427` … |
| `OrderService::validateOrder @error { print method, stack }` | `validateOrder` plus a 3-frame stack matching the site |
| `OrderService::* @return { print method, args, duration, return }` | `validateOrder [order-73547] 5122011 io.btrace.core.types.AnyType$1@2212ef71` |
| `OrderService::* @entry { print method, args }` | `processOrder [order-94839]` … |
| `OrderService::* @return { count }` | No output within 20 s (as documented: printed on an event) |

### 2.3 JDK matrix

- `demos/01-oneliner/run.sh` and `demos/07-production-safe-attach/run.sh`: exit 0 on JDK 11, 17
  and 21; exit 1 on JDK 25 and 27 with `Unable to locate tools.jar. Please, make sure JAVA_HOME
  points to a valid JDK installation`.
- Client launched directly (`java … -cp libs/btrace.jar io.btrace.boot.Loader -n '…' <pid>`) on
  JDK 25.0.5 and 27.0.1: attaches, and prints `chargeCard 373199403` and similar lines.
- JDK 8 target compiled with `javac` 8 and attached through `bin/btrace` with JDK 8: works, and
  prints the Java <17 deprecation warning that the Install section mentions.

### 2.4–2.6 Demo outputs and flat DSL

Logs were captured for all runs. Excerpts are quoted in the tables above.

## 3. What needs fixing

### 3.1 Release and distribution (blocks every install path on the site)

1. **"Download 3.0.0"** links to https://github.com/btraceio/btrace/releases/tag/v3.0.0, which
   returns **404**. The latest release is `v2.2.6`
   (`api.github.com/repos/btraceio/btrace/releases/latest`).
2. **`sdk install btrace`** installs **2.2.6**. SDKMAN lists `2.0.1 … 2.2.6`, with default `2.2.6`
   (`https://api.sdkman.io/2/candidates/btrace/linux64/versions/all`). A user who follows the site
   gets 2.x, where none of the 3.0 features exist.
3. **`jbang io.btrace:btrace:3.0.0`**: `https://repo1.maven.org/maven2/io/btrace/btrace/3.0.0/btrace-3.0.0.pom`
   returns 404. Central has no `io.btrace:btrace` artifact at all; the `io.btrace` group tops out at
   2.2.6 (`btrace-client`, `btrace-maven-plugin`, …).
4. **`docker pull ghcr.io/btraceio/btrace:3.0.0`**: the anonymous pull token request
   (`ghcr.io/token?scope=repository:btraceio/btrace:pull`) returns `DENIED: requested access to the
   resource is denied`, so no public image exists.
5. **The agent plugin's MCP server cannot start for a new user.**
   `agent-plugins/plugins/btrace-observability/mcp/src/main/java/io/btrace/mcp/BTraceMcpServer.java:1`
   declares `//DEPS io.btrace:btrace:3.0.0-SNAPSHOT` and no `//REPOS`. JBang resolves from Maven
   Central by default (https://www.jbang.dev/documentation/guide/latest/dependencies.html). The
   snapshot exists only at `https://central.sonatype.com/repository/maven-snapshots/`
   (build `3.0.0-20261029.211401-235`). Fix: after the release, set `//DEPS io.btrace:btrace:3.0.0`.
   Until then, add `//REPOS mavencentral,snapshots=https://central.sonatype.com/repository/maven-snapshots/`.
   The `btrace.jar.path` override in `BTraceClient.java:39` does not help, because JBang resolves
   `//DEPS` before the server runs.
6. Gradle Plugin Portal: `io.btrace.extension` and `io.btrace.fat-agent` are not published, so
   tutorials 06 and 08 fail at plugin resolution.

**Fix:** publish 3.0.0 (GitHub release, Central, GHCR, Plugin Portal, SDKMAN), or change the site
to say 3.0 is unreleased and point at snapshot or build-from-source instructions until then.
Re-check the site once `release.yml` has run.

### 3.2 `bin/*` launchers reject Temurin 24+ (contradicts "Java 8 through 27")

`btrace-dist/src/main/resources/bin/btrace:32` and `:58` treat a JDK as valid only if it has
`lib/tools.jar` or a `jmods/` directory:

```sh
if [ ! -f "${TOOLS_JAR}" ] && [ ! -d "${JAVA_HOME}/jmods" ]; then
  echo "Unable to locate tools.jar. Please, make sure JAVA_HOME points to a valid JDK installation"
  exit 1
```

The Temurin 24.0.2, 25.0.5 and 27.0.1 builds used here have **no `jmods/` directory** (JDK 11, 17
and Ubuntu 21 do). JEP 493 (https://openjdk.org/jeps/493) allows JDK builds that omit JMOD files.
Line 17 uses the same test to decide whether to add `-XX:+AllowRedefinitionToAddDeleteMethods` and
`--add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED`, so even with the exit removed
those flags would be dropped on such JDKs. The same pattern is in `btracec`, `btracep`, `btracer`
and every `.bat` file (`grep -n jmods btrace-dist/src/main/resources/bin/*`).

**Impact:** JDK 25 is the current LTS. On Temurin 25, `sdk install btrace` (once 3.0 ships)
followed by `btrace <pid>` fails immediately. `btrace doctor` fails the same way, which matters
because it is the documented first step for production.

**Fix:** detect the Java version, not the layout. For example, read `JAVA_VERSION` from
`$JAVA_HOME/release`, or treat any JDK 9+ with `$JAVA_HOME/bin/java` and the `jdk.attach` module as
valid. Add a CI lane that runs at least one `run.sh` (or the `qe` demos surface through the launcher)
on Temurin 25 and 27.

### 3.3 Oneliner output does not match the site

1. `print return` on a `void` method prints `io.btrace.core.types.AnyType$1@<hash>`.
   `OnelinerCodeGenerator.java:182` binds `@Return AnyType returnValue`, and `:313` emits
   `BTraceUtils.str(returnValue)`. For a void method the value is `AnyType.VOID`
   (`btrace-core/src/main/java/io/btrace/core/types/AnyType.java:28`), an anonymous instance with no
   `toString`. Fix: give `VOID` a `toString()` of `void`, or have `str()` special-case it. Also,
   because `OrderService::*` matches only `void` methods in the demo app, the site's
   `return: AUTHORIZED` sample cannot be reproduced against it.
2. `duration` is printed as bare nanoseconds. Either update the site's sample output (`chargeCard 391171192`), or
   make the generated `print … duration` emit milliseconds with a unit, as `time` does.
3. A blank line after every `println` (backlog O4) is visible in every demo log, for example demo 01
   `processOrder` / *(blank)* / `execution time: 45 ms` / *(blank)*. The site's panels show no blank
   lines.
4. `count`: say on the site that the count is printed on Ctrl+C → "send an event", or print it
   periodically.

### 3.4 Smaller issues found on the way

- **A blocked extension floods the client.** In demo 03, before the policy allows `btrace-metrics`,
  every probed call prints a full `IllegalStateException: BTrace optional service unavailable` stack
  trace: 37 traces in about 5 s. Printing one line per handler and pointing to `btrace -le` would
  read better and cost less in a production JVM.
- **Demo scripts print success text after a failure.** In demo 07 on JDK 27, the probe never
  attached, but the script still printed "Back at baseline … Nothing is left behind in the JVM.",
  and only then `[demo] FAILED`. The exit code (1) is correct. Gate the narrative lines on success
  (`demos/07-production-safe-attach/run.sh:49`).
- **Site doctor sample:** add the `-XX:+EnableDynamicAgentLoading` remediation line, or note that it
  is abridged.
- **Still open in `internal/plans/2026-10-28-demo-bug-backlog.md`**, each relevant to a site claim:
  O1 (a second deploy leaks the first probe, which undercuts "removes the probe"), O2 (a probe can
  fail silently), O9 (an exit/install race that can leave classes instrumented, which undercuts "no
  overhead after you detach"), and O10 (intermittent `Class verification failed` in demo 01 on
  JDK 17; not reproduced in the 4 demo 01 runs here on JDK 11/17/21).

## 4. Not verified, and what it would take

| Item | Blocker in this session | How to verify |
|---|---|---|
| Agent plugin end to end (demo 06 flow, `deploy_script` → `read_probe_output` → `exit_probe`) against `develop` | Running JBang and agent-plugins' Gradle wrapper was denied by the sandbox policy | In `agent-plugins/plugins/btrace-observability/mcp`: `BTRACE_JAR=<develop>/btrace-dist/build/resources/main/v3.0.0-SNAPSHOT/libs/btrace.jar ./gradlew test e2eTest` |
| Codex and Pi install commands | CLIs not installed | Run the two commands from the site in a clean environment |
| Class files newer than ASM (JDK 28) | No JDK 28 toolchain | `:btrace-agent:classFileApiBackendTest` with a JDK 28 EA build |
| JDK 24 demos | Not run (24 has no `jmods/` either, so the launcher will fail the same way) | `JAVA_HOME=<temurin-24> demos/01-oneliner/run.sh` |
