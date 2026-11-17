# BTrace Security

## Reporting Vulnerabilities

Please report security issues via GitHub Security Advisories (private disclosure) rather than public issues.

## BTRACE-005 — Pre-auth Java Deserialization via V1 Wire Protocol (2026-03)

**Affected versions:** ≤ `f5a8ef2` (and all builds using V1 `WireIO`/`JavaSerializationProtocol`).

**Vector:** Unauthenticated TCP attacker with reachability to the BTrace agent port (default `2020`) could send a crafted `SET_PARAMS` / `ERROR` / `GRID_DATA` / `NUMBER*` command. V1 used `ObjectInputStream.readObject()` with no `ObjectInputFilter` or allow-list. The agent bound to `0.0.0.0` by default and auto-downgraded V2→V1.

**Impact:** Gadget chain → RCE in the traced JVM.

### Mitigation (patched)

**Unpatched users:** bind the agent to loopback explicitly:

```
-javaagent:btrace-agent.jar=port=2020,bindAddress=127.0.0.1
```

or

```
-Dbtrace.bindAddress=127.0.0.1
```

Block the agent port at the firewall and avoid exposing it to untrusted networks.

**Patched behavior (this branch and forward-port):**

* V1 payloads rewritten to **type-safe encoding** — `TypedEncoding` (`btrace-core/.../comm/TypedEncoding.java`) handles supported value types explicitly without Java object serialization. Affects `SetSettingsCommand`, `ErrorCommand`, `NumberDataCommand`, `NumberMapDataCommand`, `GridDataCommand`, `RetransformClassNotification`, `InstrumentCommand` limits.
* Defense-in-depth `ValidatingObjectInputStream` with allow-list (`resolveClass` + reflective `ObjectInputFilter` on JDK 9+) — `btrace-core/.../comm/ValidatingObjectInputStream.java`. Used by `JavaSerializationProtocol`, the V1 protocol.
* `WireIO.read()` validates `type` range before dispatch (`IOException` not `RuntimeException`).
* Agent binds **loopback** only (default `127.0.0.1`). The `bindAddress=` agent arg selects `127.0.0.1` or `::1`; in 3.0 non-loopback and wildcard addresses such as `0.0.0.0` are rejected (`invalid agent option bindAddress=...; the command server was not started`). There is no `btrace.bindAddress` system property.

### Configuration

| Property / Arg | Default | Description |
|---|---|---|
| `bindAddress` (agent arg) | `127.0.0.1` | Loopback address for the `ServerSocket` (`127.0.0.1` or `::1`); other addresses are rejected and the command server is not started. |
| `btrace.comm.allowV1` | `true` | Gate for the V1 protocol, read by `ProtocolConfig`. Set `-Dbtrace.comm.allowV1=false` on the target JVM to reject V1 clients (V2-only). |
| `WireIO` limits | `TypedEncoding.MAX_*` | `MAX_CODE_BYTES=20MB`, `MAX_PARAMS=1000`, `MAX_KEY_LEN=4096`, `MAX_MAP_SIZE=10000`, etc. |

### Forward-Port Note

Branches after `f5a8ef2` introduced `BinaryWireProtocol` (V2, magic `BTR2`) and `JavaSerializationProtocol` (V1). The V2-only gate is defined in `ProtocolConfig` (`btrace.comm.allowV1`) and enforced in `RemoteClient`.
