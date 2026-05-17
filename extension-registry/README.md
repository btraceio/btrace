# BTrace Extension Registry Bootstrap

This directory bootstraps the dedicated GitHub-hosted BTrace extension registry described in the extension portal plan.

It is intentionally structured so it can be moved into a standalone repository with minimal changes:

- `registry/extensions.json`: canonical machine-readable registry
- `registry/extensions.schema.json`: schema contract for contributors and tooling
- `site/index.html`: minimal static portal page for GitHub Pages hosting

The validation entry point is [scripts/validate_extension_registry.py](/Users/jbachorik/src/btrace/scripts/validate_extension_registry.py:1). CI runs it for pull requests that touch the registry bootstrap.

## Entry Model

Each registry entry represents one recommended extension release and stores:

- stable `id`
- human-readable `name`
- short `description`
- `owner`
- `source_repo`
- base Maven coordinate under `maven`
- optional `tags`
- optional `compatibility.min_btrace_version`

The registry stores a single canonical coordinate plus version. Consumers are expected to resolve the standard BTrace extension artifact layout from that base coordinate.

## Moving To A Dedicated Repo

When the standalone registry repository is created:

1. Copy this directory to the new repository root.
2. Move `.github/workflows/validate-extension-registry.yml` into that repository unchanged or with path tweaks.
3. Keep `registry/extensions.json` as the canonical source reviewed through pull requests.
4. Publish `site/` with GitHub Pages.
