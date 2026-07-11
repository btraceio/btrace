# How BTrace Stays Ahead of the JDK

### A second instrumentation backend for tomorrow's class files, and a deprecation floor for yesterday's — two halves of the same forward-looking bet

BTrace's entire job is to rewrite bytecode on the fly, and that means it lives or dies by how well
it understands the class file format. For as long as BTrace has existed, that understanding has come
from ASM — a mature, fast, widely-trusted bytecode library that most of the JVM tooling ecosystem
leans on. ASM has served BTrace well. It also, as of 3.0, has a known ceiling, and BTrace 3.0 is the
release where the project starts building a path around it, while simultaneously drawing a line
under how far back it keeps investing on the other end of the version spectrum.

## The ASM ceiling

ASM parses class files by major version, and each ASM release only knows about class file versions
that existed when it shipped. The version BTrace 3.0 uses, 9.9.x, tops out at class file major
version **69**, which corresponds to Java 25. Hand it a class file compiled for Java 26 or newer —
major version 70 and up — and it throws, full stop. That's not a bug to fix; it's an inherent
property of how ASM is built, and it means that without some alternative, an application compiled
for a future JDK would simply be unparseable, and therefore un-instrumentable, by BTrace.

The fix BTrace 3.0 ships is a small internal SPI called `InstrumentationBackend`, with two
implementations behind it. `AsmInstrumentationBackend` is what's always been there — the default,
full-featured path, handling everything up to class file version 69. Sitting alongside it is
`ClassFileApiBackend`, built on `java.lang.classfile.*`, the class file API the JDK itself
standardized starting in JDK 24. Because that API ships as part of the JDK, it always understands
the class file format of whatever JDK it's running on — which makes it exactly the kind of
forward-compatible fallback ASM structurally can't be. `BackendSelector` picks between them with
logic about as simple as it looks: if the class file version is beyond what ASM supports and the
ClassFile API backend is available, use it; otherwise, use ASM.

## A backend that only runs when it can

There's a deliberate asymmetry built into how this ships. The `ClassFileApiBackend` class is
compiled against a JDK 24 toolchain and lives in its own `java24` source set — but it's loaded
*reflectively*, by class name, from `BackendSelector`, which itself is ordinary Java 8-compatible
code. On an agent running under JDK 24 or newer, that reflective load succeeds and the new backend
becomes available for anything ASM can't parse. On an agent running under an older JDK, the same
`Class.forName` call fails with an `UnsupportedClassVersionError`, which `BackendSelector` catches,
logs at debug level, and moves on — the field just stays null, and BTrace continues on ASM alone.
Note carefully what that requirement is: it's the **agent's** JDK that has to be 24+ for this backend
to engage, not the target application's. And the backend is only ever reached for class file
versions past ASM's ceiling in the first place — for every class your application actually loads
today, ASM is still doing the work, exactly as it always has.

It's also worth being precise about what "second backend" doesn't mean here: this isn't a
multi-release JAR. The `java24`-compiled class sits at the root of the regular agent jar, right next
to the Java-8-compatible classes, with no `META-INF/versions/` layout and no `Multi-Release`
manifest entry. That's safe specifically because nothing outside `BackendSelector` ever references
the class directly — the whole mechanism leans on reflection to keep the Java 8 build honest.

## What it can't do yet

We want to be plain about where this backend actually stands today, because "ClassFile API backend"
is easy to over-read as "full ASM replacement," and it isn't one. Right now it only supports two
kinds of probes — method entry and method return (`Kind.ENTRY` and `Kind.RETURN`); any handler
written for a call site, a line, a field get/set, or an error exit is skipped, with the rest of the
probe's applicable handlers still applied. Method matching only covers exact names and `/regex/`
patterns — type-constrained matching, where a handler names a specific parameter type, isn't
supported and those handlers are skipped too. Parameter injection is narrower as well: only
`@ProbeClassName`, `@ProbeMethodName`, and `@Self` are wired up; anything wanting `@Return`,
`@TargetInstance`, `@Duration`, or a probed method's own arguments doesn't get them through this
backend. Classes the ClassFile API itself can't parse are skipped with a warning rather than
crashing the target JVM. Call this what it is: a forward-compatibility safety net for the specific
case where ASM would otherwise refuse to touch a class at all — not a second, equally capable
instrumentation engine you can rely on for everything.

## The other half of the story: the Java 17 floor

The ClassFile API backend is BTrace investing in the newest end of the JDK spectrum. The other half
of "staying ahead of the JDK" is BTrace pulling back from the oldest end, and 3.0 is the release
that states this plainly for the first time:

> BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it
> continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be
> removed in the next major release (4.0).

These two things are more connected than they look. Nothing in 3.0 technically *requires* Java 17 —
the runtime still selects between tiers for 8, 9–10, 11+, and 15+ targets exactly as before, and
none of that machinery is going away this release. But look at where the actual new engineering
investment landed this cycle: a second instrumentation backend that only matters on JDK 24+, and a
round of JDK 25 compatibility fixes. That's the signal. Maintaining five separate pre-17 runtime
tiers indefinitely, while also building forward-compatibility machinery for JDKs that don't exist
yet, is a cost that only grows — so 3.0 draws the line now, with plenty of runway before anything is
actually removed. The warning is deliberately inert: it prints once, to stderr, on a JVM older than
17, tells you support is going away in the *next* major release, and points at
`-Dbtrace.suppressJavaDeprecationWarning=true` if you need it quiet for a while longer. Nothing it
does blocks an attach or fails a probe.

Put the two together and the shape of the bet is clear: BTrace 3.0 is spending its engineering
effort on the newest JDKs, giving itself an insurance policy against the oldest ASM version anyone's
running, and telling everyone still on a pre-17 JVM exactly how much runway they have before that
tier goes away. That's not a flashy release note. It's the kind of decision a tracing agent you
attach to production JVMs should be making out loud.

---

- Hands-on tutorial: [docs/tutorials/03-upgrade-from-2x.md](../../docs/tutorials/03-upgrade-from-2x.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
- Questions, or "here's a class file ASM still can't handle": [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
