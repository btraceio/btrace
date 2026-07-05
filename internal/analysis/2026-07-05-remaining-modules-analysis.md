# Wide Analysis: remaining modules (agent/instr, compiler, client, dtrace, boot, tooling, MCP)

Date: 2026-07-05
Scope: every production module NOT covered by the 2026-07-04 core/runtime round —
`btrace-agent` (agent lifecycle + the `io.btrace.instr` ASM engine), `btrace-compiler`,
`btrace-client` (CLI + `extcli`/TUI), `btrace-dtrace`, `btrace-boot`, `btrace-gradle-plugin`,
`btrace-maven-plugin`, `btrace-mcp-server`. `btrace-dist` is sample scripts only — skipped.

Method: six parallel deep-review passes, every high finding hand-verified against source. Fixes
applied this round were compiled per-module against real dependencies (ASM 9.10.1, jctools 4.0.6,
lanterna 3.1.5, slf4j, JDK compiler APIs) on JDK 21; all touched modules compile clean, and the
changed files were formatted with google-java-format to satisfy spotless.

Severity: **HIGH** = broken feature / hang / data corruption / FD exhaustion on a realistic path;
**MED** = wrong behavior or race on a plausible path; **LOW** = latent / edge / hygiene.

Legend: ✅ fixed this round · ⏸ deferred (needs instrumentation golden-file regeneration, which
requires the full dist build + JDK 8/11 toolchains).

---

## HIGH

### ✅ H-A1. Compiler hangs forever on any enum-declared method call
`btrace-compiler/.../VerifierVisitor.java:146` — the `do { parent = (TypeElement) e.getEnclosingElement(); }
while (parent.getKind() != CLASS && parent.getKind() != INTERFACE)` loop never reassigns `e`, so when
the method's declaring type is an ENUM/ANNOTATION/RECORD the condition stays true forever. Any script
calling e.g. `TimeUnit.NANOSECONDS.toMillis(x)` spins `btracec`/the agent compiler at 100% CPU.
**Fix:** resolve the declaring type once (`e.getEnclosingElement()` is always the declaring
`TypeElement` for a method) and accept any type kind.

### ✅ H-A2. `DTraceDropCommand.write` serializes the stream instead of the event
`btrace-dtrace/.../DTraceDropCommand.java:45` — `out.writeObject(out)` (should be `out.writeObject(de)`),
a copy-paste typo. `read()` expects a `DropEvent`; writing the `ObjectOutput` throws
`NotSerializableException` on every drop event. **Fix:** `out.writeObject(de)`.

### ✅ H-A3. Agent leaks a socket FD per non-instrument connection
`btrace-agent/.../RemoteClient.getClient` returns `null` (EXIT), loops-until-EOF (`-lp`/`-le`), or throws
(negotiation failure / malformed) without ever closing the accepted `Socket`; the accept loop in
`Main` ignored the return and only logged. A monitoring script polling `btrace -lp` exhausts the target
JVM's descriptors. **Fix:** in `Main`'s accept loop, close the socket whenever `getClient` returns null
or throws (a non-null return means the live client owns it). Hoisted `sock` out of the try and added
`closeQuietly`.

### ✅ H-A4. `-Dio.btrace.core.cmdQueueLimit` from the client is silently dropped
`btrace-client/.../Client.java:708` builds the segment `",=" + CMD_QUEUE_LIMIT + value` →
`,=cmdQueueLimit<value>` instead of `,cmdQueueLimit=<value>`. The agent splits on the first `=`, gets an
empty key, and never matches the `CMD_QUEUE_LIMIT` case, so the target always uses the default queue
limit. **Fix:** `"," + CMD_QUEUE_LIMIT + "=" + value` (matching every sibling arg).

### ✅ H-A5. MCP server dies on one malformed JSON line
`btrace-mcp-server/.../BTraceMcpServer.run` calls `protocol.readMessage()` outside the try; `parseJson`
throws unchecked `IllegalArgumentException`, which propagates to `main` (catches only `IOException`) and
kills the process for all subsequent requests. **Fix:** catch the parse error in the loop and reply with
a JSON-RPC `-32700` (id null), then continue. Also fixed `readMessage` treating a blank line as EOF (now
skips blank lines; null means only true EOF).

### ⏸ H-A6. `@Duration` on a `Kind.CALL` handler always receives 0
`btrace-agent/.../Instrumentor.java:664` — `onAfterCallMethod` declares a *local*
`MethodTrackingContext trackingCtx` that shadows the anonymous class's configured field (set by
`onBeforeCallMethod`'s `emitEntry`). `emitExit`/`emitTestSample` run on the fresh, un-timed local while
`injectBtrace`'s duration provider reads the field, whose timing test was never emitted → `emitDuration`
emits `ldc 0L`. Adaptive-sampled CALL sites also lose their end-timestamp feedback. RETURN/ERROR/ENTRY use
the shared field context and are unaffected. **Fix (deferred):** reuse the field context in
`onAfterCallMethod`. Changes emitted bytecode → needs golden-file regeneration.

### ⏸ H-A7. Maven-embedded extensions are non-functional (wrong `.classdata` path)
`btrace-maven-plugin/.../FatAgentMojo.stageImplClasses` stages impl classes under
`META-INF/btrace-extensions/<id>/impl/<pkg>/Foo.classdata`, but the runtime `ClassDataLoader.findClass`
resolves `<pkg>/Foo.classdata` at the classpath root (as the Gradle fat-agent plugin and dist build
stage them). Every service/impl class throws `ClassNotFoundException` at runtime. **Fix (deferred):**
stage impl `.classdata` at the staging root; low-risk but Maven-plugin packaging isn't buildable/testable
in this environment — grouped with the other Maven-plugin embedding gaps (missing `version`/
`btrace.api.version` in the minimal-props fallback) for a single packaging-focused change.

---

## MEDIUM

### ✅ M-A1. Client CLI hangs on an empty argument token
`btrace-client/.../Main.java:158` — `for(;;) { if (args[count].isEmpty()) continue; ... }` skips the
tail `count++`, spinning forever on an empty arg. **Fix:** advance `count` (with bounds check) before
`continue`.

### ✅ M-A2. Agent `Client.WRITER_MAP` is a plain HashMap mutated cross-thread
`btrace-agent/.../Client.java:87` — `setupWriter` (accept/main thread) does get+put, `closeAll`
(command-handler/executor thread) does remove, with no synchronization; a concurrent put during resize
can corrupt the table (JDK 8 spin) and the check-then-act can create duplicate writers. **Fix:**
synchronize the get-or-create block and the removal on the shared `WRITER_MAP` monitor.

### ✅ M-A3. `@TLS short` / `@Export short` probe fields fail with VerifyError
`btrace-agent/.../TypeUtils.isPrimitive(String)` omits `case 'S'`, so `Preprocessor.initAnnotatedField`
skips the `Short.valueOf` box node and passes a raw short where an `Object` is expected. **Fix:** add
`case 'S'` (the `boxNode`/`BOX_TYPE_MAP` already handles `"S"`).

### ✅ M-A4. `@OnProbe`-mapped Location is shared across sessions
`btrace-agent/.../OnMethod.copyFrom` did `setLocation(other.getLocation())` — sharing the `Location`
object with a process-wide-cached `ProbeDescriptor` template that `applyArgs` then mutates in place, so
two sessions using the same `@OnProbe` namespace corrupt each other's substituted values. **Fix:** added a
`Location` copy constructor and deep-copy in `copyFrom`.

### ✅ M-A5. `CompilerHelper` permanently trusts the JVM + leaks a classloader
`btrace-compiler/.../CompilerHelper.java:255` did `SharedSettings.GLOBAL.setTrusted(true)` on the
process-wide singleton and never restored it (disabling verification for the JVM's life, racy across
compilations), and never closed the per-class `URLClassLoader`. **Fix:** save/restore the previous
trusted value in a finally, and wrap the classloader in try-with-resources.

### ✅ M-A6. MCP handler client sockets leaked
`ListProbesHandler` (transient client never closed), `ExitProbeHandler`/`DetachProbeHandler`
(`sendExit`/`sendDisconnect` + `removeClient` without `close()`). **Fix:** close the client in a finally /
after the send.

### ✅ M-A7. Oneliner `args[N] == null` filter crashes the parser
`btrace-compiler/.../oneliner/OnelinerAST` — the `Filter` base constructor did
`Objects.requireNonNull(value)`, but `ArgFilter` legitimately carries a null value for null comparison
(the parser produces it and codegen has a dedicated branch). **Fix:** allow a nullable `value` in the
base constructor.

### ✅ M-A8. `BTraceTransformer` regex probe never unregisters cleanly
`Filter.add` keys regex matchers by `getClazz().replace("\\.", "/")` but `Filter.remove` used
`replace('.', '/')`; for a regex with escaped dots the keys differ, so the pattern entry is never removed
(leaked entries, `matchClass` returning stale MAYBE/TRUE, broken refcount on re-register). **Fix:** align
`remove` with `add`'s key computation.

### ⏸ M-A9. Nested `new` breaks `Kind.NEW, Where.AFTER` with `@Return`
`btrace-agent/.../ObjectAllocInstrumentor` tracks a single `boolean instanceCreated`; `new Outer(new
Inner())` collapses it so the outer constructor's `afterObjectNew` never fires. **Fix (deferred):** use a
stack of pending NEW types. Changes emitted bytecode → needs golden-file regeneration.

---

## LOW (representative; fixed where safe)

- ✅ **MethodTracker** parallel sampling arrays made `volatile` (hot-path readers index them without
  locking while `registerCounter` reassigns on growth → stale/short-array AIOOBE).
  `btrace-agent/.../MethodTracker.java`.
- ✅ **PolicyFile.save** guarded `Files.createDirectories(target.getParent())` against a null parent (bare
  relative `--policy-file` filename). `btrace-client/.../extcli/PolicyFile.java`.
- ⏸ **LineNumberInstrumentor:46** fires `onAfterLine(line-1)` instead of `onAfterLine(lastLine)` — wrong
  line for non-consecutive line tables (bytecode change → deferred).
- ⏸ **gradle plugin** `@ExtensionDescriptor` scanned under the wrong package `org/openjdk/...` (same class
  of defect as the already-known `ServiceDescriptor` mismatch) — package-level declared permissions never
  collected; plus `buildApiJar` input under-declaration causing stale manifests. Grouped for a
  plugin-focused pass.
- Assorted verified-latent (no live caller): `Assembler.invokeInterface` emits INVOKEVIRTUAL+isInterface;
  `VariableMapper.isInvalidMapping` mask is `0xFFFFFFFF`; `BTraceClassReader.getClassVersion` returns
  `(minor<<16)|major`; `AnnotationSerializer.serialize` produces malformed output. Left as-is (documented).
- Assorted client/dtrace/boot leaks and edge bugs: `FileClient.loadWithSecurity` JarFile leak +
  dead `canLoadPack` gate; `JpsUtils` MonitoredVm handles never detached; `Installer.downloadToTemp`
  never `disconnect()`s and rejects cross-protocol redirects; `Loader.getJarFile` fallback doesn't
  URL-decode `%20`; `DTraceExtension` double-close; `ExtensionReport.toJson` doesn't escape control chars;
  `ExtRepoBrowser` reads the shared `all` list off the GUI thread unsynchronized. Individually minor;
  candidates for a follow-up cleanup pass.

---

## Deferred set (why)

The four ⏸ instrumentation items (H-A6 CALL `@Duration`, M-A9 nested NEW, LineNumber off-by-one) change
the bytecode the instrumentors emit, so landing them flips the `instrumentorTestData` golden files. Those
must be regenerated with `./gradlew test -PupdateTestData`, which needs the full distribution build and
the JDK 8/11 toolchains — not available in this environment. They are high-confidence and worth doing;
they just need to be applied where the golden suite can be regenerated and diffed. H-A7 and the
gradle/maven-plugin items are packaging changes best validated with an actual plugin build.
