# Debug a Live JVM in One Line

### No agent to configure, no restart, no code change — just a question and an answer, in under two minutes

Picture the most common debugging moment there is: something in production is slow, sometimes, and
you don't know why. Not crashed, not throwing five-alarm errors — just occasionally, quietly, worse
than it should be. You could add logging and redeploy. You could attach a profiler and wade through
a flame graph looking for a signal. Or you could ask the running JVM directly, in one line, and get
an answer before your coffee gets cold.

That's the entire premise of BTrace's oneliner language, and it's the fastest on-ramp into BTrace
3.0. Here's what it actually looks like end to end, using the same order-processing demo app that
opens the hands-on tutorial.

*[terminal recording]*

## The setup: an app that's lying to you, a little

The demo app is a small, single-file order service with two defects baked in on purpose. You run it
with nothing but `java DemoApp.java`, and it starts logging summary lines like `processed 64 orders,
8 failed`. Orders are moving. Some are failing. The app itself won't tell you why — it just reports
the aggregate and moves on. That's the entire prompt: something's wrong, and the only source of
truth is the JVM that's actually running it.

## Step one: watch a method, live

Find the process ID with `jps`, then ask BTrace to watch a single method:

```sh
btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

That's the whole first move — no compile step, no script file, no restart. BTrace parses the
oneliner into a small instrumentation program, attaches to the live JVM, hooks the return of
`OrderService.processOrder`, and starts streaming timing for every call while the app keeps serving
orders. What comes back is a stream of execution times, and if you watch it for even a few seconds
you'll notice the pattern: most calls finish in double digits of milliseconds, but every so often one
takes 300-plus. That's not noise. That's the bug announcing itself.

## Step two: isolate the slow path

Widen the net to every method on `OrderService`, then keep only the calls that cross a threshold:

```sh
btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <PID>
```

The slow calls now show up paired: `chargeCard` and `processOrder`, always together, both slow at
the same moment. Case closed — `chargeCard` is calling out to a slow payment provider that hits
roughly one call in ten, and that latency is exactly what's dragging `processOrder` down with it.
You found the actual culprit, by name, without opening a single source file, using a filter you
typed on the command line.

## Step three: catch the exception the app is hiding from you

The demo app's summary line also reports failed orders, and there's a second defect behind that
number: an exception that's being thrown, caught, and silently swallowed somewhere in the order
validation path. A oneliner can hook the error exit of a method specifically:

```sh
btrace -n 'OrderService::validateOrder @error { print method, stack }' <PID>
```

Run that, and for roughly one order in twelve you get the exception's exact origin and call stack —
live, from a method the application itself never surfaces. No log line was added for this. No code
was touched. You just asked the JVM what actually happened, and it told you.

## Detach, and nothing is left behind

`Ctrl+C` in the BTrace terminal detaches the client, and in 3.0 that's a clean, total stop — every
injected hook becomes a no-op on the spot, with no lingering overhead and no restart required to
remove it. Reattach whenever you want; the app never noticed you were there.

Total elapsed time for all three of the moves above: about five minutes, with the first real
answer landing well inside the first two. That's not a demo trick — it's the actual shape of an
oneliner session against a real, misbehaving app.

## About the 30-second start

The fastest possible path into BTrace, in principle, is [JBang](https://www.jbang.dev/): no
install, just `jbang btrace@btraceio <PID> script.java` and you're attached. We want to be upfront
about where that stands today — the external `btraceio/jbang-catalog` that powers the short
`btrace@btraceio` alias hasn't been updated for 3.0 yet and still points at old 2.x coordinates, so
that particular shortcut isn't reliable against a 3.0 build right now. The longer form,
`jbang io.btrace:btrace-client:<version> <PID> script.java` with an explicit version, is the safe
substitute until the catalog is refreshed — and everything else in this post, the oneliners
themselves, works exactly as shown against any 3.0 install.

## Why this is the on-ramp

Oneliners aren't a toy subset of BTrace — they're the same instrumentation engine, the same
`@OnMethod`/`@Return`/`@Error` semantics that back full scripts, compressed into a single
expression you can type at a shell prompt. When a oneliner stops being enough — when you want state
that persists across calls, or a histogram instead of a print stream — it graduates cleanly into a
real script, and that's a five-minute story of its own. But for the everyday "why is this slow, and
what exactly failed" question, one line and under two minutes is the whole answer.

---

- Hands-on tutorial: [docs/tutorials/01-first-trace-in-2-minutes.md](../../docs/tutorials/01-first-trace-in-2-minutes.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
- Questions, war stories, or "here's the bug I found" reports: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
