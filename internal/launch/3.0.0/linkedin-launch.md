# LinkedIn — launch post (2.x users and tracing-tool people)

Status: draft, 2026-10-26. About 210 words. Attach `docs/tutorials/images/01-oneliner.gif` once
rendered. Post first; the AI post follows one or two days later.

---

BTrace 3.0.0 is out. If you used BTrace before, or you use any JVM tracing tool, here is what
changes on Monday morning.

No file, no compile. Attach to a running JVM with one line:

btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <pid>

Scripts are half the size. The flat DSL drops the BTraceUtils. prefix and the import block.

Attach to production without fear. Start the JVM with -javaagent:btrace.jar=port=0 and you get a
loopback, token-protected endpoint. btrace doctor <pid> tells you it is ready before anything is
loaded. When your client exits, the probe's call sites become no-ops. Nothing lingers.

Extensions replace libs= profiles and the Aggregations API. HdrHistogram percentiles come from
btrace-metrics, and when an extension is blocked, btrace -le tells you why instead of failing
silently.

Upgrade cost: compiled 2.x probes need no change. Source scripts need a one-line import fix, and
there is a script for it.

Also in the box: one btrace.jar instead of three, Apache 2.0, a binary wire protocol with 2.x
fallback, and a ClassFile API backend for class files newer than ASM can parse.

There is a second story about coding agents and AI workloads. That one gets its own post.

Release notes: https://github.com/btraceio/btrace/releases/tag/v3.0.0
Migration guide: https://github.com/btraceio/btrace/blob/v3.0.0/docs/Migration-2.x-to-3.0.md

#java #jvm #observability #opensource
