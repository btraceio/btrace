# BTrace Permission Policy (Simplified)

Minimal, per-extension policy that decides whether an extension may link its implementation. API
types remain available on the bootstrap classpath, so a probe that declares the service as
`@Injected(optional = true)` still deploys when the implementation is blocked: it gets a throwing
stub by default, or a no-op SHIM with `mode = SHIM` / `-Dbtrace.extension.shimMode=shim`. A plain
`@Injected` field never falls back; deploying such a probe fails fast.

## Sources (first found wins)
- `-Dbtrace.permissions=/path/to/permissions.properties`
- `~/.btrace/permissions.properties` or `~/.config/btrace/permissions.properties`
- Classpath resource `META-INF/btrace/permissions.properties`

## Agent Arguments (optional)
- `allowExtensions=<id1,id2>`: allow specific extension IDs to link implementations.
- `denyExtensions=<id1,id2>`: block specific extension IDs (optional injections fall back, non-optional ones fail).
- `allowPrivileged=true|false`: allow all privileged extensions to link implementations.
- The `grant=`, `deny=` and `grantAll=` agent arguments are accepted for compatibility but have no
  effect: only the three options above and the policy file decide whether an extension links.

## File Format (properties)
- `allowExtensions=id1,id2`
- `denyExtensions=id3,id4`
- `allowPrivileged=false`

Example:
```
allowExtensions=btrace-statsd,my-metrics
denyExtensions=legacy-foo
allowPrivileged=false
```

## Behavior
- If an extension ID is denied, the implementation is not linked; APIs remain available so optional
  injections can fall back to a throwing stub or SHIM.
- If an extension is privileged (declares any privileged required permissions) and is neither
  explicitly allowed nor covered by `allowPrivileged=true`, its implementation is blocked (optional
  injections fall back to a throwing stub or SHIM; non-optional ones fail the deployment).
- “Required permissions” remain visible in logs and listings to inform the operator’s decision; no
  fine-grained per-permission policy evaluation is performed.
