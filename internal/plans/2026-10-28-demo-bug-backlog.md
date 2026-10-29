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

### O4. `println` output reaches the client with an extra blank line

- **Where:** btrace runtime/client message formatting (not root-caused).
- **What:** every `println(...)` line arrives followed by an empty line, both through the `btrace`
  CLI and through the MCP server (`read_probe_output` returns `screen calls=196\n\n`).
- **Repro:** any probe with two `println` calls; compare with the expected one line each.

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

### O9. A probe exit that overlaps the probe's own install can leave classes instrumented

- **Where:** `Client.retransformLoaded` (install, on `Main`'s `serializedExecutor`) and
  `Client.onExit` (on the runtime's command thread); not synchronized with each other.
- **What:** not observed; found while root-causing F11. If an exit arrives while the install
  retransform is still running, the install thread's redefinition can land after the restoring
  one and reinstall the instrumented bytes. The same can happen between one client's exit and
  another client's install, since exits are not serialized with installs.
- **Suggested fix:** run the restoring retransform through the same serialized executor, or have
  `retransformLoaded` re-check `isShuttingDown()` and retransform again after the install finishes.

### O10. Intermittent "Class verification failed" on a repeated oneliner against the same class

- **Where:** agent instrumentation (not root-caused).
- **What:** demo 01's third probe on `OrderService`
  (`btrace -n 'OrderService::validateOrder @error { print method, stack }'`, after two earlier
  probes on the same class were retracted) logged `[BTRACE WARN] Class verification failed:
  OrderService (null)` and produced no output, though the client reported the probe started. Seen
  1 of 4 runs on JDK 17.0.19; the `(null)` means the cause is not reported either.
- **Repro:** run `demos/01-oneliner/run.sh` repeatedly on JDK 17.

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
