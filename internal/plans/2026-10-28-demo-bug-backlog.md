# BTrace bugs found while building the 3.0.0 demos

Running the demo scenarios end to end, and recording real AI-assistant sessions against them, has
been the most productive bug hunt of the release. This file collects every BTrace and BTrace MCP
server defect found that way, so the open ones can be fixed. Keep it current: add new findings at
the end of "Open", and move an item to "Fixed" with its commit when it lands.

Repositories: `btrace` (this one) and `btraceio/agent-plugins` (the `btrace-observability` plugin
and its MCP server, under `plugins/btrace-observability/mcp`).

## Open

### O1. A second deploy on the same JVM leaks the first probe (MCP server)

- **Where:** agent-plugins, `ClientManager.registerClient` and the two deploy handlers.
- **What:** the server keeps one session per `pid:port`. Deploying a probe while another deployed
  in the same session is still active replaces the registry entry without stopping or closing the
  first one. Its client socket stays open and the probe keeps running in the target, but
  `exit_probe` now only reaches the second probe, and `list_probes` (which lists only detached
  probes) does not show it either.
- **Impact:** an assistant can truthfully believe it cleaned up while a probe is still running.
  Seen in a recorded take (demo 06, take B): it deployed `Probe7`, then `Probe8` without an
  `exit_probe` in between, and finally reported "no probes are still attached".
- **Repro:** `deploy_oneliner` twice on the same PID, `exit_probe` once, then check the target
  (for example a counter probe still firing, or `jcmd <PID> Thread.print` showing the BTrace
  command thread).
- **Not confirmed live:** the take B service was stopped when the recording ended, so the leak was
  inferred from the code path, not observed in that JVM.
- **Suggested fix:** refuse a deploy while a session for that `pid:port` is active ("exit or detach
  the running probe first"), or track several probes per JVM. Test-first in `ToolHandlersTest`.

### O2. A probe can fail silently with no output at all

- **Where:** btrace agent, instrumentation of `Kind.CALL` handlers (suspected).
- **What:** this script, deployed through the MCP server against demo 06's checkout service,
  deployed successfully and printed nothing for 23 s, not even from its plain `Kind.RETURN`
  handler on `FraudScreen.screen`, which runs on every checkout:

  ```java
  @OnMethod(clazz = "com.acme.payments.PaymentClient", method = "authorize",
      location = @Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/", where = Where.AFTER))
  public static void a(@ProbeClassName String pc, @ProbeMethodName String pm,
      @TargetInstance Object ti, @TargetMethodOrField String tm, @Duration long d) { ... }

  @OnMethod(clazz = "com.acme.risk.FraudScreen", method = "screen",
      location = @Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/", where = Where.AFTER))
  public static void s(@TargetInstance Object ti, @TargetMethodOrField String tm, @Duration long d) { ... }

  @OnMethod(clazz = "com.acme.risk.FraudScreen", method = "screen", location = @Location(Kind.RETURN))
  public static void r(@Return Object v) { println(strcat("verdict=", str(v))); }
  ```

- **Not yet root-caused.** Suspects: the CALL handlers with `@TargetInstance` over calls to static
  methods (`Thread.sleep`) or a verification failure in one handler disabling the whole class,
  with nothing reported to the client.
- **Repro:** start demo 06 (`demos/06-ai-assistant/run.sh`), deploy the script above with
  `btrace <PID> Probe2.java`, wait 20 s. Then drop handlers one at a time to find the trigger.
- **Expected:** every handler that matches runs; a handler that cannot be applied is reported.

### O3. Unannotated exception parameters at CATCH and THROW are silently ignored

- **Where:** btrace agent, `Instrumentor` CATCH and THROW cases; more generally,
  `validateArguments`.
- **What:** at `Kind.CATCH` and `Kind.THROW` the exception binds only to `@TargetInstance`. A plain
  `Throwable t` parameter is matched against the target method's own arguments, fails, and the
  handler is never applied, with no compile-time or runtime message. `Kind.ERROR` had the same
  problem and is fixed (see F10).
- **Broader issue:** any handler whose unannotated parameters do not match the target's arguments
  is skipped silently. A verifier warning ("handler X matches no method signature of Y") would
  have turned three of the bugs in this file into a one-line error.
- **Suggested fix:** bind a bare `Throwable` at CATCH and THROW as at ERROR, and report handlers
  that are never applied because of an argument mismatch.

### O5. `str()` on an enum prints its identity, not its name

- **Where:** `BTraceUtils.str(Object)`.
- **What:** `str(verdict)` for `com.acme.risk.Verdict.HOLD` prints
  `com.acme.risk.Verdict@778d07d8`. A recorded assistant had to correlate verdicts by identity hash.
- **Suggested fix:** for enum constants, return `name()` (safe: no user `toString` is called).

### O6. `list_jvms` keeps saying "BTrace: attached" after every probe is removed (MCP server)

- **Where:** agent-plugins, `ListJvmsHandler`.
- **What:** the flag reflects that the agent is loaded, which is permanent once attached, not that
  a probe is running. Every recorded assistant then has to explain why "attached" does not mean a
  probe is left behind.
- **Suggested fix:** report "agent loaded, no active probes" (or the probe count) instead.

### O7. Assistants keep writing 2.x imports, and the errors don't help

- **Where:** btrace compiler diagnostics (and the MCP server's compile error text).
- **What:** in recorded takes, assistants wrote `org.openjdk.btrace.core.*` (the 2.x package) in
  three of six first deploys, and once `io.btrace.annotations.*`, which fails with "reference to
  BTrace is ambiguous" because `io.btrace.BTrace` exists in the root package.
- **Suggested fix:** when compiling a script that imports `org.openjdk.btrace`, either migrate the
  source as `ProbePackageMigrator` does for compiled probes or fail with "BTrace 3 moved
  `org.openjdk.btrace` to `io.btrace`; replace the imports". Consider whether `io.btrace.BTrace` in
  the root package is needed.

### O8. Build and test tooling papercuts found on the way

- `publishBtracePublicationToMavenLocal` requires a PGP signature, so a snapshot cannot be
  installed locally for JBang or Maven users without `mvn install:install-file`. Skip signing for
  `mavenLocal`.
- Running `:btrace-agent:test --tests '*OnMethodInstrumenterTest*'` alone fails nearly every
  golden case, although the same tests pass in the full task: the class depends on setup done by
  other tests.
- `:btrace-agent:classFileApiBackendTest` needs a JDK 28 toolchain and fails `./gradlew build` on a
  machine without one instead of being skipped with a message.
- Even with `-x signBtracePublication`, the dist publication still expects
  `btrace-agent-fat.jar.asc` (`btrace-dist/build.gradle:1013-1020`), and the Gradle plugin refuses
  to publish a SNAPSHOT version (`btrace-gradle-plugin/build.gradle:83-105`), so neither can be
  staged locally for a tutorial run without workarounds.
- `:btrace-dist:buildDockerContext` fails Gradle's implicit-dependency validation: it reads
  `copyExtensions` output without declaring a dependency on it.

### O9. A probe exit that overlaps the probe's own install (investigated, not reproduced)

- **Where:** `Client.retransformLoaded` (install, on `Main`'s `serializedExecutor`) and
  `Client.onExit` (on the runtime's command thread); not synchronized with each other.
- **Concern:** found while root-causing F11. If the install's transformer call ran before the exit
  unregistered the probe, but its redefinition landed after the exit's restore, the classes would
  stay instrumented.
- **Investigation (2026-10-29):** 3000 matching classes, `-Dbtrace.retransform.batchSize=4000` to
  put them in one retransform, client killed 1.6–3.4s after launch, timestamped temporary logs
  around install and exit. On JDK 21 an exit unregistered the probe mid-install (unregistered <
  install end < restored) and throughput recovered fully; no run on JDK 11, 17 or 21 stayed
  instrumented. Every JDK tested (8u442, 11.0.23, 17.0.19, 21.0.5, 25.0.2) has HotSpot's
  `RedefineClasses_lock`, which as I understand `VM_RedefineClasses` holds per class from before the
  ClassFileLoadHook until the new version is installed. Two retransforms of the same class then
  serialize, transformer call included, and the later one sees the current probe registry. The
  interleaving above needs the transform to run outside that lock, so it should not happen on
  HotSpot.
- **Status:** no code change. Reopen if a JVM without that serialization is supported, or if a
  stuck class is seen after an early exit. A side observation: on JDK 11 a single 3000-class
  retransform took ~100s (restore ~160s), so large `btrace.retransform.batchSize` values are a bad
  idea there.

### O10. Demo 01's second probe was still registered when the third installed (most likely F11, not reproduced)

- **Where:** client shutdown and agent exit path.
- **What:** in the demo 01 run that hit F12, the third probe's install saw the second probe
  (`OrderService::* @return if duration>200ms`) still registered, although `btrace_until` waits for
  the second client to exit before starting the third.
- **Investigation (2026-11-01):** that run (2026-10-28 11:58 UTC) predates the F11 fix
  (`8ab60867`, 2026-10-29). On TERM the client's shutdown hook sends `ExitCommand` and exits
  without waiting for the echo; before F11 an echo that hit the closed socket skipped
  `Client.onExit` and left the probe registered for good, which is what the third probe met 1–2 s
  later. No other way for an exited probe to stay registered was found. The demo 01 sequence was
  rerun on builds from just before F11 (`68a623c2`) and with F11 only (`8ab60867`), both without
  F12 so a leftover probe shows as the verification warning: 0 hits in 24 runs each (Oracle JDK
  17 idle and under CPU load, Temurin 17.0.19 idle). The echo rarely loses the race against
  demo 01's mostly idle app, unlike demo 07's hot loop where it lost every time.
- **Status:** no code change; F11's regression test covers the failure. Reopen if an exited probe
  is seen still registered on a build with F11. A possible hardening, not done: have the client's
  shutdown hook wait briefly for the exit echo, so the next probe always starts after cleanup.

### O11. The plugin's MCP server cannot resolve BTrace for a fresh user (agent-plugins)

- **Where:** agent-plugins, `plugins/btrace-observability/mcp/.../BTraceMcpServer.java:1`.
- **What:** `//DEPS io.btrace:btrace:3.0.0-SNAPSHOT` with no `//REPOS` line. A clean `jbang` resolve
  fails with "Could not find artifact io.btrace:btrace:jar:3.0.0-SNAPSHOT in mavencentral", so
  the plugin's `btrace` MCP server never starts. It works here only because of `~/.m2`.
- **Fix:** depend on `3.0.0` when the release is published.

### O12. Pi installs the skills but not the `btrace` MCP server (agent-plugins)

- **Where:** agent-plugins `package.json:20`.
- **What:** `"mcp": "./plugins/jafar-perf/.mcp.json"` registers only the jafar server, so tutorial
  05's flow cannot work on Pi. Whether Pi honours the key at all is not verified.

### O13. `list_probes` cannot see connected probes

- **Where:** `btrace-agent/src/main/java/io/btrace/agent/Client.java:279-288` (lists only
  `isDisconnected()` clients); agent-plugins `ListProbesHandler` (also never closes its client).
- **What:** `list_probes` returned an empty list while a probe was active and printing, so tutorial
  05's "check that no probe is left" step proves nothing for a connected probe. O1 and O6 still
  reproduce as of agent-plugins `bdda18f`.

### O14. Cross-container attach fails when the target's working directory is read-only (not root-caused)

- **What:** a target started with `-w /app` on a `:ro` bind mount could not be attached from a
  sidecar: "Unable to open socket file /proc/1/root/tmp/.java_pid1 ... doesn't respond". The same
  setup with a writable working directory works.

### O15. Smaller findings from the landing-page and tutorial check

- The required-service refusal names the probe's internal class (`io.btrace.runtime.auxiliary.X`)
  instead of the script.
- With real Shadow the extension impl jar also contains the API classes (in-repo too, e.g.
  `btrace-contracts-*-impl.jar`); check whether the duplicates matter for class loading.
- Launching the client through JBang or `java -jar` skips `-XX:+AllowRedefinitionToAddDeleteMethods`,
  which only the shell launchers pass.

## Fixed

| # | Defect | Fix |
|---|---|---|
| F1 | Oneliner class and method wildcards (`OrderService::*`) never matched | `OnelinerCodeGenerator.toProbePattern`, 2026-10-26 |
| F2 | Probes compiled with BTrace 2.2.6 failed verification | `ProbePackageMigrator` before verification, 2026-10-26 |
| F3 | Each `@Injected` read site bootstrapped its own extension service instance | `ExtensionIndy` instance cache, 2026-10-26 |
| F4 | `BTraceClassReader`'s ASM hook broke with ASM 9.11.1, and had always called the wrong method | `BTraceClassReader`, 2026-10-26 |
| F5 | The annotation-processor service entry in `btrace.jar` broke single-file source launch on JDK 25+ | entry dropped, Gradle plugin passes `-processor`, 2026-10-26 |
| F6 | The extension Gradle plugin's shim generator declared no inputs, leaving stale shims | inputs declared, 2026-10-26 |
| F7 | MCP server dropped all probe output after startup | agent-plugins `bdda18f`: buffer, `read_probe_output`, output from `exit_probe` |
| F8 | `@Duration` at `Kind.RETURN` was 0 at every return after the first in bytecode order | `c81a2d43` |
| F9 | `BTraceRuntimeImplBaseTest` raced the command thread and failed on slow CI runners | `d6065f3b` |
| F10 | A plain `Throwable` parameter at `Kind.ERROR` was silently ignored | `068e1a81` |
| F11 | A client that exited right away could leave its probe installed: the failed exit echo skipped `Client.onExit` (demo 07 throughput stuck at a third of baseline) | `8ab60867` |
| F12 | An `@error` probe added after a `@return` + `@Duration` probe on the same method failed verification ("Class verification failed: ... (null)" in demo 01): the earlier probe declared its duration locals at the later probe's handler | `InstrumentingMethodVisitor`, 2026-11-01 |
| F13 | Demos failed with "Port 2020 unavailable." while another BTrace agent held the default port | `common.sh` picks the next free port, the client message suggests `-p`, 2026-11-01 |
| F14 | `println` output arrived with an extra blank line (former O4), and `print()` still ended the line | `020c7d82`, `8d90ab3d` |
| F15 | Launchers treated JMOD-less JDKs (JEP 493: Temurin 24, 26) as pre-9 and refused to start | `5efe4f7c` |
| F16 | The site's oneliner builder generated `print duration` (nanoseconds) but showed `execution time: N ms` | gh-pages `81fbc0ca` |
| F17 | The full Debian Docker image set a non-existent JAVA_HOME, and the build hid it | `2d0c5170` |
| F18 | The extension Gradle plugin could never auto-apply Shadow for external consumers (now needs Gradle 9.2+) | `31424a83` |
| F19 | The extension version was captured at plugin apply time, giving `unspecified` in the manifest | `4eacf060` |
| F20 | Fat agents warned about duplicate embedded extensions when the same jar was seen twice | `db9c593b` |
| F21 | Compiler errors reported wrong line numbers, and a commented import disabled the auto-import | `997e13ae` |
| F22 | `-l` and `~name` crashed when the client ran without the shell launcher (JBang, `java -jar`) | `caa2e60b` |
| F23 | `btracex policy set` could not flip an earlier deny to allow | `5816049e` |
| F24 | A probe with an unavailable required `@Injected` service deployed and then threw at every call | `52c4c34f` |
| F25 | The migration script listed unchanged lines after applying | `0adb2d22` |
