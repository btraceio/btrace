# Agent Manifest Library Attributes

The BTrace agent JAR manifest can declare extra JARs to place on the bootstrap and
system class loaders, so a repackaged or fat agent can carry its dependencies without
extra command-line arguments. These attributes are read from the agent JAR's
`META-INF/MANIFEST.MF` at startup.

## Attributes

| Manifest attribute | Purpose |
|---|---|
| `BTrace-Boot-Libs` | Space-separated list of JAR paths appended to the **bootstrap** class loader search. |
| `BTrace-System-Libs` | Space-separated list of JAR paths appended to the **system** class loader search. |
| `BTrace-Libs-Root` | Optional base directory for resolving relative entries. Defaults to the agent JAR's parent directory. |
| `BTrace-Libs-Profile` | Optional named subdirectory to auto-scan for `boot/*.jar` and `system/*.jar`. Mirrors the `libs` agent argument. |

The standard `Boot-Class-Path` manifest attribute continues to be honored as well.

Entries use `Class-Path`-style space separation; each entry may be absolute or relative
to `BTrace-Libs-Root` (or the agent JAR's parent). Unusable entries are logged and skipped
rather than aborting startup.

## See also

- [Masked JAR architecture](MaskedJarArchitecture.md)
- [Migrating from libs/profiles to Extensions](migrating-from-libs-profiles.md)
