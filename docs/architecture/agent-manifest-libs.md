# Agent Manifest Library Attributes

The BTrace agent JAR manifest can declare extra JARs to place on the bootstrap and
system class loaders, so a repackaged or fat agent can carry its dependencies without
extra command-line arguments. These attributes are read from the agent JAR's
`META-INF/MANIFEST.MF` at startup, but only when the target JVM runs with
`-Dbtrace.feature.manifestLibs=true` (the feature is experimental and off by default);
`-Dbtrace.ignoreManifestLibs=true` turns it off again.

## Attributes

| Manifest attribute | Purpose |
|---|---|
| `BTrace-Boot-Libs` | Space-separated list of JAR paths appended to the **bootstrap** class loader search. |
| `BTrace-System-Libs` | Space-separated list of JAR paths appended to the **system** class loader search. |
| `BTrace-Libs-Root` | Optional directory holding the profile directories that `BTrace-Libs-Profile` selects. Defaults to `btrace-libs` beside the agent JAR, or beside its parent when that parent is named `libs`. |
| `BTrace-Libs-Profile` | Optional named subdirectory to auto-scan for `boot/*.jar` and `system/*.jar`. Selects a profile directory by name; the former `libs` agent argument no longer exists. |

The standard `Boot-Class-Path` manifest attribute continues to be honored as well.

Entries use `Class-Path`-style space separation; each entry may be absolute or relative
to the agent JAR's parent directory. Entries that do not exist, are not `.jar` files, or lie
outside the BTrace home (unless `-Dbtrace.allowExternalLibs=true`) are logged and skipped
rather than aborting startup.

## See also

- [Masked JAR architecture](MaskedJarArchitecture.md)
- [Migrating from libs/profiles to Extensions](migrating-from-libs-profiles.md)
