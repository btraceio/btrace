# X — launch thread (2.x users and tracing-tool people)

Status: draft, 2026-10-26. Seven posts, each under 280 characters. Post 1 carries the GIF from
`docs/tutorials/images/01-oneliner.gif`. Post 5 can carry `07-production-safe-attach.gif`.

---

1/
BTrace 3.0.0 is out. First major release since 2.2.6.

If you trace JVMs for a living, here is what changes on Monday morning. Thread.

2/
No file, no compile. One line attaches to a running JVM:

btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <pid>

Edit the matcher, run again. Nothing to restart.

3/
When a oneliner is not enough, scripts are half the size. The flat DSL drops the BTraceUtils.
prefix and the import block. A probe reads like the question it answers.

4/
Extensions replace libs= profiles and the Aggregations API. Percentiles come from btrace-metrics.
A blocked extension is explained by btrace -le, not swallowed.

5/
Attach to production without fear.

-javaagent:btrace.jar=port=0 gives a loopback, token-protected endpoint. btrace doctor <pid> says
if it is ready before anything loads. When the client exits, the probe's call sites become no-ops.

6/
Upgrade cost: compiled 2.x probes need nothing. Source scripts need a one-line import fix, and
scripts/migrate-btrace-script.sh does it.

Also: one btrace.jar, Apache 2.0, binary wire protocol with 2.x fallback, ClassFile API backend.

7/
Release notes: https://github.com/btraceio/btrace/releases/tag/v3.0.0

There is a second story about coding agents and AI workloads. Next thread.
