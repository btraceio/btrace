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
-agentpath:btrace-agent.jar=port=2020,bindAddress=127.0.0.1
```

or

```
-Dbtrace.bindAddress=127.0.0.1
```

Block the agent port at the firewall and avoid exposing it to untrusted networks.

**Patched behavior (this branch and forward-port):**

* V1 payloads rewritten to **typed primitive schemas** — `TypedEncoding` (`btrace-core/.../comm/TypedEncoding.java`) with explicit `TAG_*` bytes, no `readObject`/`writeObject`. Affects `SetSettingsCommand`, `ErrorCommand`, `NumberDataCommand`, `NumberMapDataCommand`, `GridDataCommand`, `RetransformClassNotification`, `InstrumentCommand` limits.
* Defense-in-depth `ValidatingObjectInputStream` with allow-list (`resolveClass` + reflective `ObjectInputFilter` on JDK 9+) — `btrace-core/.../comm/ValidatingObjectInputStream.java`. Used by `RemoteClient.getClient()`.
* `WireIO.read()` validates `type` range before dispatch (`IOException` not `RuntimeException`).
* Agent default bind changed to **loopback** (`127.0.0.1`). Override with `bindAddress=<host>` agent arg or `-Dbtrace.bindAddress=<host>`. Non-loopback binds log `WARN`; `0.0.0.0` explicitly warns `unauthenticated remote access allowed`.

### Configuration

| Property / Arg | Default | Description |
|---|---|---|
| `bindAddress` (agent arg) / `btrace.bindAddress` (system prop) | `127.0.0.1` | Address for `ServerSocket`. Set to `0.0.0.0` to restore pre-patch exposure (not recommended). |
| `btrace.comm.allowV1` | `true` on V1-only branches, `false` on V2 branches | Gate for V1 protocol. On branches with `ProtocolConfig`/`ProtocolNegotiator` (`f5a8ef2` lineage), V1 is rejected unless `allowV1=true`. See `FORWARD_PORT.md`. |
| `WireIO` limits | `TypedEncoding.MAX_*` | `MAX_CODE_BYTES=20MB`, `MAX_PARAMS=1000`, `MAX_KEY_LEN=4096`, `MAX_MAP_SIZE=10000`, etc. |

### Forward-Port Note

Branches after `f5a8ef2` introduced `BinaryWireProtocol` (V2, magic `BTR2`) and `JavaSerializationProtocol` (V1). The V2-only gate is defined in `ProtocolConfig` (`btrace.comm.allowV1`) and enforced in `RemoteClient` / `ProtocolNegotiator`. See `docs/FORWARD_PORT_BTRACE_005.md` for the exact patch.
