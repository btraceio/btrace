# Forward-Port BTRACE-005 to f5a8ef2+ / develop

This worktree (`cosmic-feathery-finch` @ `7fb5c31d`) is V1-only. Later lineages (`f5a8ef2` onward) split into `WireIO` (V1) + `BinaryWireProtocol` (V2) via `ProtocolNegotiator`/`ProtocolConfig`.

## Gate: V2-only by default

On V2 branches, V1 must be opt-in via `btrace.comm.allowV1`:

1. **`btrace-core/.../comm/ProtocolConfig.java`** — add:
   ```java
   private static final String PROP_ALLOW_V1 = "btrace.comm.allowV1";
   public boolean isV1Allowed() { return Boolean.getBoolean(PROP_ALLOW_V1); }
   // Or: Boolean.parseBoolean(System.getProperty(PROP_ALLOW_V1, "false"))
   ```
   Default `false` (V2-only). Document in `SECURITY.md`.

2. **`ProtocolNegotiator.java` / `RemoteClient.java`** — after `negotiate()` / `detectFromPrefix()`:
   ```java
   if (negotiated == ProtocolVersion.V1 && !ProtocolConfig.fromSystemProperties().isV1Allowed()) {
     throw new IOException("V1 protocol disabled by security policy. "
         + "Set -Dbtrace.comm.allowV1=true to re-enable for legacy clients.");
   }
   ```
   Log `WARN` when V1 is allowed.

3. **`JavaSerializationProtocol.java`** — wrap its `ObjectInputStream` with `ValidatingObjectInputStream` (already done in this worktree's `RemoteClient`). Do not create raw `ObjectInputStream` elsewhere.

4. **Typed schemas** — cherry-pick from this worktree: `TypedEncoding.java`, `ValidatingObjectInputStream.java`, and the rewrites of `SetSettingsCommand`, `ErrorCommand`, `NumberDataCommand`, `NumberMapDataCommand`, `GridDataCommand`, `RetransformClassNotification`, `InstrumentCommand` limits, `WireIO` range-check. These files exist in both lineages under the same package.

5. **Bind address** — already cherry-picked: `Main.java` loopback default via `bindAddress` arg / `btrace.bindAddress` prop.

## Cherry-pick commands

```bash
git checkout <target-branch>  # e.g. develop or release branch containing f5a8ef2+
git cherry-pick <this-branch> --no-commit  # or format-patch
# Expected files:
#  btrace-core/.../comm/TypedEncoding.java
#  btrace-core/.../comm/ValidatingObjectInputStream.java
#  btrace-core/.../comm/{SetSettingsCommand,ErrorCommand,NumberDataCommand,NumberMapDataCommand,GridDataCommand,RetransformClassNotification,InstrumentCommand,WireIO}.java
#  btrace-agent/.../Main.java (bindAddress) + RemoteClient.java (validating stream)
#  docs/SECURITY.md + docs/FORWARD_PORT_BTRACE_005.md
# Then manually add the ProtocolConfig/ProtocolNegotiator gate above (no equivalent file in this worktree).
```

## Verification

* `grep -rn readObject btrace-core/src/main/java/org/openjdk/btrace/core/comm` → only comments.
* `grep -rn ValidatingObjectInputStream` → `RemoteClient` + `JavaSerializationProtocol`.
* `-Dbtrace.comm.allowV1=false` + V1 client → `IOException: V1 protocol disabled`.
* `-Dbtrace.comm.allowV1=true` + V1 client → succeeds with typed schema (no gadget).
* Loopback default: `ss = new ServerSocket(port, 50, loopback)` verified.
