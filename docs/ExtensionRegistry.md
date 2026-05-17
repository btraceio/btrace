# BTrace Extension Registry

The BTrace extension registry is a small GitHub-maintained catalog of extensions published as Maven artifacts. Its purpose is discovery, not artifact hosting: each registry entry points to the source repository and the canonical Maven coordinate for the recommended current release.

## Current Bootstrap Location

The registry bootstrap currently lives in this repository under [extension-registry/](/Users/jbachorik/src/btrace/extension-registry/README.md:1). That directory is structured so it can be moved into a dedicated GitHub repository later without changing the JSON contract:

- [registry/extensions.json](/Users/jbachorik/src/btrace/extension-registry/registry/extensions.json:1): canonical registry entries
- [registry/extensions.schema.json](/Users/jbachorik/src/btrace/extension-registry/registry/extensions.schema.json:1): schema contract
- [site/index.html](/Users/jbachorik/src/btrace/extension-registry/site/index.html:1): minimal GitHub Pages portal

CI validates the registry with [scripts/validate_extension_registry.py](/Users/jbachorik/src/btrace/scripts/validate_extension_registry.py:1).

## Entry Format

Each extension entry includes:

- `id`: stable extension id
- `name`: human-readable name
- `description`: short catalog summary
- `owner`: publisher or GitHub owner
- `source_repo`: GitHub repository URL
- `maven.groupId`
- `maven.artifactId`
- `maven.version`
- optional `compatibility.min_btrace_version`
- optional `tags`

The registry stores one canonical coordinate per extension release line. BTrace consumers are expected to resolve the standard BTrace extension artifact layout from that base coordinate.

## Contribution Model

The intended long-term workflow is:

1. Publish the extension to Maven Central.
2. Open a pull request against the registry repository.
3. Add or update the extension entry in `extensions.json`.
4. Let CI validate schema, uniqueness, and basic metadata shape.
5. Merge to publish the updated JSON and GitHub Pages catalog.

For first-party BTrace extensions, this registry should be updated whenever a new public extension is released or an existing one changes its recommended version.
