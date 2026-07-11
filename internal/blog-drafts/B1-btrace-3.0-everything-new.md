# BTrace 3.0: Everything New, and the Road to 4.0

### A new name, a new license, one jar, and a clear runway to what comes next

BTrace 3.0 is out, and it's the biggest release in the project's history — not because any single
feature is flashy, but because three foundational things changed at once: the package name, the
license, and how the whole thing ships. If you've used BTrace before, none of your probes are about
to stop working. If you're new here, this is a good day to start, because 3.0 is also the release
where BTrace tells you, plainly, where it's headed next.

## The identity changes

BTrace's Java package moved from `org.openjdk.btrace` to `io.btrace`, and the Maven/Gradle group ID
moved with it. That's a breaking change on paper, but in practice it's the most gently-landed
breaking change we could manage: compiled and persisted probes built against the old namespace keep
working with zero action from you, because the agent rewrites the legacy references to `io.btrace`
in memory the moment it loads them. Source scripts need a one-line import fix, and there's a script
in the box that does it for you (more on that below).

Alongside the rename, BTrace relicensed from GPLv2 with the Classpath Exception to the **Apache
License, Version 2.0**. And the distribution itself got simpler: instead of separate agent, boot,
and client jars, BTrace 3.0 ships as a single masked `btrace.jar`. Its manifest declares
`io.btrace.boot.Loader` as the `Premain-Class`, `Agent-Class`, and `Main-Class` all at once, and
internally it uses classdata masking so only a small bootstrap API is visible to your application's
classloader while the agent, client, and instrumentation engine stay invisible. One jar to copy, one
jar to reference in a launch script, one jar to `-javaagent:` onto a JVM.

Underneath those headline changes, 3.0 also brings a permission-based extension framework, a flat
scripting DSL that lets probes call `println`, `str`, and friends with zero imports, a DTrace-style
oneliner language for attaching from the command line in a single command, an MCP server that lets
an AI assistant drive a live diagnostic session, and a new binary wire protocol between client and
agent. Each of those deserves — and is getting — its own post. This one is about what changes for
you today, and what BTrace is asking you to plan for.

## What do I need to do?

If you have compiled or persisted probes: **nothing.** That's the honest, load-bearing answer, and
it's worth leading with because it covers more BTrace users than any other category. Everything else
is a short, mechanical list:

| What you have | What you must do |
|---|---|
| Compiled/persisted probes (`.class`) | **Nothing** — the agent auto-migrates them at load time |
| BTrace script sources (`.java`) | **Nothing** for most scripts — or a one-line rename (see below) |
| Mixed 2.x/3.0 client and agent | **Nothing** — the wire protocol auto-negotiates |
| Maven/Gradle dependencies | Update coordinates to `io.btrace:btrace` |
| Launch scripts referencing multiple BTrace jars | Point to the single `btrace.jar` |
| `libs=` / profiles agent options | Migrate to extensions (deprecated but still working) |
| Target JVMs on Java 8–16 | **Nothing** — deprecated, but fully supported throughout 3.x |

For script sources, the entire migration surface is the package prefix: `org.openjdk.btrace`
becomes `io.btrace`, everywhere it appears as an import or a fully-qualified reference. Because
the 3.0 compiler now auto-injects the flat DSL and annotation imports into any script that doesn't
already import them, most scripts can drop their BTrace imports entirely rather than rewrite them.
And if you'd rather not do it by hand, `scripts/migrate-btrace-script.sh` rewrites the prefix for
you (with a `--dry-run` preview and a `.bak` backup), single file or recursively over a directory.

If you're still using `libs=`/profile agent options, that path is deprecated in favor of the
extension framework — isolation, permissions, and per-extension enable/disable instead of mutating
the global classpath. It keeps working in 3.x, with a runtime warning, while you migrate at your own
pace.

## The Java version deprecation

This is the other half of the 3.0 story, and it's a first for the project: BTrace is putting a
deprecation floor under the Java versions it targets. The policy, stated once so it can be repeated
everywhere:

> BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it
> continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be
> removed in the next major release (4.0).

Nothing in 3.0 actually *requires* Java 17 — under the hood BTrace still selects between runtime
tiers for 8, 9–10, 11+, and 15+ targets, exactly as it always has. This is a forward-looking
maintenance decision, not a technical one: the newest investment (a second instrumentation backend
built on the JDK's own ClassFile API, JDK 25 compatibility work) all lands on the modern-JDK side,
and carrying five separate pre-17 code tiers indefinitely has a real cost. So 3.x keeps everything
working, and 4.0 is where the pre-17 tiers get retired.

In practice, here's what you'll see. If you attach BTrace to a target JVM older than Java 17, the
agent prints this once, to stderr, the first time it starts on that JVM:

```
[BTrace] WARNING: This JVM is Java <N>. Running BTrace on Java versions older than 17 is deprecated and support will be removed in the next major release. Please upgrade to Java 17 or newer. Suppress this warning with -Dbtrace.suppressJavaDeprecationWarning=true.
```

You'll see a matching notice on the client console when you attach, too. Nothing about the warning
enforces anything — no probe is refused, no attach is blocked — and if you're deliberately running
a fleet on older JDKs during a gradual migration, set `-Dbtrace.suppressJavaDeprecationWarning=true`
on the target JVM and it stays quiet.

Java < 17 isn't the only thing on a removal timeline in 3.0 — the `libs=`/profiles agent option
carries the same "deprecated now, gone later" shape, on its own separate schedule. We're calling
both out together in these release notes rather than letting either one be a surprise later.

## The road to 4.0

Think of 3.0 as the release that draws the line, not the one that crosses it. Everything that works
today keeps working today. What changes is that BTrace now has an explicit, published answer to
"how long will this keep working," instead of an implicit one nobody wrote down. That's worth having
in a tracing agent you attach to production JVMs — you get to plan your Java upgrades on your own
schedule, with real notice instead of a surprise major-version break.

If you want the full mechanical rundown — coordinates, packaging, the `libs=` migration path, the
wire-protocol compatibility guarantee — that's what the migration guide is for. If you want to see
the deprecation warning and the migration script in action against a real script, the upgrade
tutorial walks through both in about ten minutes.

---

- Hands-on tutorial: [docs/tutorials/03-upgrade-from-2x.md](../../docs/tutorials/03-upgrade-from-2x.md)
- Full migration guide: [docs/Migration-2.x-to-3.0.md](../../docs/Migration-2.x-to-3.0.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
- Questions, war stories, or "here's what broke for me" reports: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
