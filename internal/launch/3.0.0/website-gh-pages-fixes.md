# Pending btrace.io/btrace fixes (gh-pages)

Found in the 3.0.0-RC1 pre-release check (2026-11-16) against `gh-pages` @ `d16ebbcf`, the commit
GitHub Pages serves (`index.html` there is byte-identical to https://btrace.io/btrace/). Not pushed
from that session, which could push only its own development branch.

`website-gh-pages.patch` (next to this file) changes two sentences in `site/landing.html`:

| Section | Was | Why it is wrong | Now |
|---|---|---|---|
| Install the agent plugin | "its MCP server depends on a 3.0.0 snapshot that JBang cannot resolve from Maven Central, so it will not start for new users until 3.0.0 is released" | `agent-plugins` `plugins/btrace-observability/mcp/src/main/java/io/btrace/mcp/BTraceMcpServer.java:1` declares `//REPOS mavencentral,central-snapshots=https://central.sonatype.com/repository/maven-snapshots/`, and that repository serves `io.btrace:btrace:3.0.0-SNAPSHOT` (`maven-metadata.xml`, build 309, 2026-11-16) | "until 3.0.0 is released, its MCP server runs a 3.0.0 snapshot build of BTrace, which JBang downloads from the Sonatype snapshot repository" |
| Demos | "CI replays every demo on each push" | The `qe` job (`.github/workflows/continuous.yml`) replays each demo's probes against its target app through `btrace-qe/cases/demos/*.yaml`, not the `run.sh` scripts, and not demo 06's agent session | "CI replays the probes of every demo against its target app on each push" |

Apply and rebuild (Node.js 18+, see `docs/Website.md` on `gh-pages`):

```bash
git checkout gh-pages
git apply website-gh-pages.patch
npm ci && npm run build
git add site/landing.html index.html assets/render.js
```

Everything else on the page was checked against this repository at the same time: every link
(tutorials, docs, demos, anchors) resolves, the three oneliner presets and the doctor samples match
real output on JDK 21, and the version and pre-release statements are true until 3.0.0 is released.
After the final 3.0.0 release, the "3.0.0 is not released yet", "SDKMAN! still installs 2.2.6", the
`v3.0.0-SNAPSHOT` badge and the plugin pre-release note must be updated.
