# Under the Hood: The V2 Wire Protocol

### BTrace 3.0 replaces Java serialization on the wire between client and agent — here's what changed and why, and what we're not claiming yet

Every time a BTrace client talks to a BTrace agent — sending a script to instrument, receiving a
print statement, streaming a histogram back — something has to travel over a socket as bytes. For
every release before 3.0, that something was a `Command` object handed to Java's built-in
`ObjectOutputStream`. It worked. It also carried all the baggage that comes with Java serialization:
reflection-heavy encode/decode, verbose stream metadata for every object graph, and a hard
dependency on having a JVM on both ends of the wire. BTrace 3.0 introduces a second option — a
custom binary protocol, internally called V2 — sitting behind an abstraction that lets the two
coexist without either side needing to know which one it's talking to.

## The problem with Java serialization, structurally

None of this is a knock on `ObjectOutputStream` for what it's designed to do. But as a wire format
for a tracing agent, it has three structural costs. First, it's slow relative to a purpose-built
binary encoding, because it has to walk object graphs and write type descriptors alongside the
actual data. Second, it's large on the wire for the same reason — every command pays for metadata
it doesn't strictly need. Third, and least obviously, it locks the protocol to Java: a serialized
`Command` object can only be read back by another JVM with the matching classes on its classpath,
which forecloses the idea of a BTrace client written in Python, Go, or JavaScript.

BTrace 3.0's answer is `BinaryWireIO` — a hand-rolled binary format that encodes each command as a
version byte, a type byte, and then the command's own fields written directly (four-byte big-endian
integers, length-prefixed UTF-8 strings, and so on), with no class metadata riding along. Payloads
over 1KB get run through `Deflater`/`Inflater` compression automatically. All seventeen command
types — `MESSAGE`, `INSTRUMENT`, `STATUS`, `GRID_DATA`, and the rest — have binary counterparts, and
a `CommandAdapter` converts losslessly between the old `Command` representation and the new
`BinaryCommand` one, so the rest of the agent and client codebase didn't have to be rewritten around
the new format.

## Negotiation: nobody has to choose ahead of time

The part of this design worth dwelling on is that you, the user, never pick a protocol version. When
a client opens a connection, it sends a four-byte magic prefix — `0x42 0x54 0x52 0x32`, "BTR2" in
ASCII — and waits for the same bytes back. An agent that understands V2 echoes the magic and the
connection locks to the binary protocol for its entire lifetime. An agent that doesn't recognize the
magic — because it's still running an old BTrace build — simply doesn't respond, the client's wait
times out, and it falls back to sending a plain Java-serialization stream instead, which any BTrace
agent, however old, already knows how to read. The negotiation happens exactly once per connection,
not once per command, so its cost is fixed and gets amortized over however many thousands of
commands a session ends up sending. The practical result: a 2.x client can attach to a 3.0 agent, and
a 3.0 client can attach to an old agent, and neither side needs to be told which era the other one is
from.

If you want to reach past the default behavior, three system properties are the real, current
knobs — set them on whichever side you want to influence:

```
-Dbtrace.comm.protocol=v2          # preferred version: 1, 2, v1, or v2 (default v2)
-Dbtrace.comm.autoNegotiate=true   # negotiate automatically (default true)
-Dbtrace.comm.forceVersion=true    # skip negotiation, use the configured version outright (default false)
```

There's also `-Dbtrace.protocol.negotiation.timeout=5000`, which controls how long a client waits
for the BTR2 echo before giving up and falling back to V1. The negotiation logic itself lives in
`ProtocolNegotiator`, the abstraction both protocols implement is `WireProtocol`, and the binary
encoders live under `io.btrace.core.comm.v2` — worth a look if you're curious what a
`BinaryInstrumentCommand` actually looks like on the wire.

## What we're deliberately not telling you

Here's the part of this post we want to be straightforward about. The architecture doc for this
protocol includes tables of specific performance numbers — serialize/deserialize timings, wire-size
comparisons, throughput figures, all framed as measurements. We're not reproducing any of those
numbers in this post, and that's a deliberate choice, not an oversight. There *is* a reproducible
harness: a JMH benchmark (`BinaryProtocolBenchmark`, under `btrace-core/src/jmh`) that times binary
versus Java serialization, deserialization, and round trips for the same commands, runnable with
`./gradlew :btrace-core:jmh` and wired into the V2 protocol CI workflow as an on-demand job. What
the repository doesn't contain is a committed set of results from running it on a fixed, described
environment — and the doc's own "post-release technical debt" list still includes a V2-only
end-to-end integration suite and stress tests under sustained high-frequency tracing. The
responsible thing is to not put a multiplier in front of you that isn't backed by a run of that
harness you could repeat.

What we can tell you honestly, from the design itself: a binary format with no reflection and no
class metadata will be smaller and cheaper to encode/decode than Java serialization for the same
data, and compression on payloads over 1KB will shrink anything text-heavy further. Those are true
by construction. The specific "how much" is a number we still owe you, and when we publish it —
from the JMH harness above, on a fixed environment, with the command line to reproduce it — that's
the post that gets the table. This one gets the honest version instead.

## Why it's worth knowing about even though it's invisible

For the overwhelming majority of BTrace users, V2 is something that happens without being asked
about — you attach, you get the faster protocol, you never see a version number. It's worth knowing
about anyway for two reasons: if you're building tooling against BTrace in another language someday,
a documented binary format is a much better foundation than "whatever `ObjectOutputStream` happens
to produce." And if something ever looks wrong on the wire — a version mismatch, an agent that
refuses to negotiate — knowing that `-Dbtrace.comm.forceVersion=true -Dbtrace.comm.protocol=1` gets
you back to the plain-Java-serialization baseline is exactly the kind of thing you want to know
before you need it, not while you're debugging it at 2am.

---

- Hands-on tutorials (there's no dedicated protocol lab — the protocol is exercised by every one of them): [docs/tutorials/README.md](../../docs/tutorials/README.md)
- Architecture reference: [docs/architecture/Version2ProtocolArchitecture.md](../../docs/architecture/Version2ProtocolArchitecture.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
<!-- TODO: replace with the per-post Discussions thread before publishing -->
- Questions, or "here are the numbers I got from the JMH harness": [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
