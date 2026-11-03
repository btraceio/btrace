# BTrace bugs found while building the 3.0.0 demos

Running the demo scenarios end to end, and recording real AI-assistant sessions against them, has
been the most productive bug hunt of the release. This file collects every BTrace and BTrace MCP
server defect found that way, so the open ones can be fixed. Keep it current: add new findings at
the end of "Open", and move an item to "Fixed" with its commit when it lands.

Repositories: `btrace` (this one) and `btraceio/agent-plugins` (the `btrace-observability` plugin
and its MCP server, under `plugins/btrace-observability/mcp`).

## Open

### O8. Build and test tooling papercuts found on the way

- The Gradle plugin refuses to publish a SNAPSHOT version
  (`btrace-gradle-plugin/build.gradle:83-105`), so it cannot be staged locally for a tutorial run
  without workarounds.
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
  reproduced as of agent-plugins `bdda18f`; O6 has since been fixed (F39).

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

### O16. On JDK 24+ the target app prints a `sun.misc.Unsafe` removal warning once a probe attaches

- **Where:** the JCTools 4.0.7 copy shaded into the agent (`io/btrace/libs/org/jctools`); no BTrace
  source calls `sun.misc.Unsafe::arrayBaseOffset`.
- **What:** in demo 07 on Temurin 26.0.1 the app's own output shows
  `WARNING: sun.misc.Unsafe::arrayBaseOffset will be removed in a future release` between the
  throughput lines. It is the one-time warning the JDK prints under the default
  `--sun-misc-unsafe-memory-access=warn`; the other demos pass with no such line, and JDK 11, 17
  and 21 print nothing. The integration harness already filters the line out of oneliner output.
- **Why it matters:** it lands in the user's application log, and a later JDK that defaults to
  `deny` would make these queues throw instead of warn.
- **Suggested fix:** not investigated. Options: a JCTools release whose queues avoid
  `sun.misc.Unsafe`, JCTools' non-Unsafe (atomic) queue variants, or `java.util.concurrent`
  queues where the hot path allows.

### O17. A `Kind.LINE` probe ignores the `method` of its `@OnMethod`

- **Where:** btrace agent, `Instrumentor.visitMethod`, which applies a `Kind.LINE` probe to every
  method of a matching class without looking at `method`.
- **What:** a probe with `method = "screen"` and `line = -1` on `FraudScreen` also fired on the lines
  of `deepScore` (46 to 50). The behaviour looks long-standing, and the `AllLines` test trace names
  `<init>` yet instruments every method, so a fix may be a behaviour change rather than a plain bug
  fix. Decide which one before changing it.
- **Repro:** start demo 06, deploy that probe and look at the reported line numbers.

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
| F26 | A `Kind.CALL` `@Duration` probe whose call site pattern matched the `System.nanoTime()` call BTrace adds overflowed the stack while instrumenting, and no handler ran in the class | `Instrumentor`, 2026-11-01 |
| F27 | A class that failed to transform was reported only in the target JVM's log, so its probes printed nothing with no explanation | `BTraceTransformer`, 2026-11-01 |
| F28 | A `Kind.CALL` `@Duration` probe failed verification when the call sat inside a branch: a label's saved state came from the last jump to it, and an injected frame displaced the method's own frame at the same offset | `InstrumentingMethodVisitor`, `LastFrameAtOffsetVisitor`, 2026-11-01 |
| F29 | A `Kind.LINE` probe on a line starting at a catch handler or join point failed verification, because its code went in front of the stack map frame of that offset | `LineNumberInstrumentor`, 2026-11-01 |
| F30 | A plain `Throwable` parameter at `Kind.CATCH` and `Kind.THROW` was silently ignored | `BTraceMethodNode`, 2026-11-01 |
| F31 | A handler whose unannotated parameters fit no matching method was skipped with no message to the client | `MethodInstrumentor`, `Instrumentor`, 2026-11-01 |
| F32 | `str()` printed an application enum constant as its identity instead of its name | `BTraceUtils`, 2026-11-01 |
| F33 | Compile errors for 2.x and other wrong imports did not say what to write instead, and reported lines shifted by the auto-imports | `CompilerHelper`, `Compiler`, 2026-11-01 |
| F34 | A snapshot could not be installed into `mavenLocal` without a PGP key | `btrace-dist/build.gradle`, 2026-11-01 |
| F35 | `classFileApiBackendTest` failed `./gradlew build` on a machine without JDK 28 | `btrace-agent/build.gradle`, 2026-11-01 |
| F36 | `OnMethodInstrumenterTest` failed when run on its own | `InstrumentorTestBase`, 2026-11-01 |
| F37 | A second deploy on one JVM left the first probe running where `exit_probe` could not reach it | agent-plugins `ClientManager`, deploy handlers, 2026-11-01 |
| F38 | The MCP server's busy-port error told the user about the CLI's `-p` flag, and its e2e test failed against the current client | agent-plugins `BTraceClient` (#18), 2026-11-02 |
| F39 | `list_jvms` said "attached" for any JVM with the agent loaded, even after every probe was removed | agent-plugins `ListJvmsHandler` (#17), 2026-11-03 |
