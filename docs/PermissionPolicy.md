# BTrace Permission Policy (Simplified)

Minimal, per-extension policy that decides whether an extension may link its implementation. API
types remain available on the bootstrap classpath so SHIMs can be generated when implementations
are blocked.

## Sources (first found wins)
- `-Dbtrace.permissions=/path/to/permissions.properties`
- `~/.btrace/permissions.properties` or `~/.config/btrace/permissions.properties`
- Classpath resource `META-INF/btrace/permissions.properties`

## Agent Arguments (optional)
- `allowExtensions=<id1,id2>`: allow specific extension IDs to link implementations.
- `denyExtensions=<id1,id2>`: block specific extension IDs (SHIM fallback only).
- `allowPrivileged=true|false`: allow all privileged extensions to link implementations.
- Launch-time grants (e.g., `grant=...`, `deny=...`, `grantAll=...`) remain supported but are not
  part of the policy file and are not required for allow/deny decisions.

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
- If an extension ID is denied, the implementation is not linked; APIs remain available for SHIMs.
- If an extension is privileged (declares any privileged required permissions) and is neither
  explicitly allowed nor covered by `allowPrivileged=true`, its implementation is blocked (SHIMs
  still available).
- “Required permissions” remain visible in logs and listings to inform the operator’s decision; no
  fine-grained per-permission policy evaluation is performed.
