# Upgrade from 2.x in 10 Minutes

Move your scripts and deployments to BTrace 3.0. For almost everything, the answer to "what do I
need to change?" is **nothing** — this tutorial proves it, and shows you the one-line fix for the
rest.

**Persona:** BTrace 2.x users planning a move to 3.0. **Time:** ~10 minutes.

## What you'll need

- BTrace 3.0 installed — `bin/btrace` on your PATH ([installation options](../GettingStarted.md#installation))
- A checkout of the BTrace repository (for `scripts/migrate-btrace-script.sh`) — or copy that one
  script to your machine
- Optionally, one of your own BTrace 2.x script sources to migrate for real

## Step 1 — See what actually needs to change

BTrace 3.0 renamed its package from `org.openjdk.btrace` to `io.btrace`. Here's a small,
representative 2.x-style probe ([demo/legacy/SlowChargeProbe.java](demo/legacy/SlowChargeProbe.java))
— the kind of thing you might have sitting in a `scripts/` directory:

```java
package demo;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Return;
import static org.openjdk.btrace.core.BTraceUtils.println;
import static org.openjdk.btrace.core.BTraceUtils.str;

@BTrace
public class SlowChargeProbe {
  @OnMethod(clazz = "demo.OrderService", method = "chargeCard")
  public static void onCharge(@Duration long duration, @Return Object result) {
    if (duration > 200_000_000L) {
      println("slow chargeCard: " + str(duration / 1_000_000) + " ms");
    }
  }
}
```

Every one of its imports is a `org.openjdk.btrace.*` reference. That's the entire migration
surface — there's no other syntax change between 2.x and 3.0.

## Step 2 — Preview the fix

BTrace ships a small helper that rewrites exactly that prefix. Preview what it would change,
without touching anything:

```sh
scripts/migrate-btrace-script.sh --dry-run SlowChargeProbe.java
```

**You should see:**

```
would migrate: SlowChargeProbe.java
    3:import org.openjdk.btrace.core.annotations.BTrace;
    4:import org.openjdk.btrace.core.annotations.OnMethod;
    5:import org.openjdk.btrace.core.annotations.Duration;
    6:import org.openjdk.btrace.core.annotations.Return;
    7:import static org.openjdk.btrace.core.BTraceUtils.println;
    8:import static org.openjdk.btrace.core.BTraceUtils.str;
Dry run: 1 file(s) would be migrated (of 1 examined).
```

## Step 3 — Apply it

```sh
scripts/migrate-btrace-script.sh SlowChargeProbe.java
```

**You should see:**

```
migrated: SlowChargeProbe.java (backup: SlowChargeProbe.java.bak)
    3:import io.btrace.core.annotations.BTrace;
    4:import io.btrace.core.annotations.OnMethod;
    5:import io.btrace.core.annotations.Duration;
    6:import io.btrace.core.annotations.Return;
    7:import static io.btrace.core.BTraceUtils.println;
    8:import static io.btrace.core.BTraceUtils.str;
Done: 1 file(s) migrated (of 1 examined).
```

Your original file is preserved as `SlowChargeProbe.java.bak`; the script itself now imports
`io.btrace.*` and deploys against BTrace 3.0 exactly as before — `@BTrace`, `@OnMethod`,
`@Duration`, `@Return`, and `BTraceUtils.println`/`str` all exist with the same names and
signatures in 3.0.

> **What just happened?** The tool does one thing: it finds `org.openjdk.btrace` (as an import or
> a fully-qualified reference) and replaces it with `io.btrace`, everywhere in the file. That's the
> entire migration for script *sources*. Pass `-r` to point it at a whole directory of scripts
> instead of one file at a time.

> **Going further:** since the 3.0 compiler auto-injects `import static io.btrace.BTrace.*;` and
> `import io.btrace.core.annotations.*;` into any script that doesn't already import the DSL or
> the annotations package, you can now *delete* most of those six import lines entirely and keep
> only the logic. Migrating gets you back to 3.0's zero-import style for free — see
> [From Oneliner to Script](02-oneliner-to-script.md) for that DSL.

## Step 4 — What about probes you already deployed?

If you have *compiled* `.class` probes — persisted, pre-built, deployed as part of some other
tool — you don't need to touch them at all, and you don't need the migration script for them
either. When the BTrace 3.0 agent loads a probe class file, it scans the bytecode for
`org/openjdk/btrace/` references and, only if it finds any, rewrites every class, method, field,
descriptor, and annotation reference to `io/btrace/` **in memory**, before the probe runs. A
`.class` file with no legacy references is loaded unchanged — there's no overhead for probes that
are already on the new namespace. This also covers probes compiled against the even older
pre-2.0 `com/sun/btrace` namespace.

There is nothing to run for this step — it's automatic, every time the agent loads a probe. See
[Migrating from 2.x to 3.0](../Migration-2.x-to-3.0.md) for the full picture (Maven/Gradle
coordinates, the single `btrace.jar`, the `libs=`-to-extensions path, and wire-protocol
compatibility).

## Step 5 — Check your target JVM's Java version

BTrace 3.0 still fully supports Java 8 through the latest release — but running against a JVM
**older than Java 17 is now deprecated**. Nothing breaks; you'll just see a one-time notice.

Check what your target is running:

```sh
java -version
```

If it reports anything below `17`, the next time you attach BTrace to it you'll see, once, on the
agent side:

```
[BTrace] WARNING: This JVM is Java <N>. Running BTrace on Java versions older than 17 is deprecated and support will be removed in the next major release. Please upgrade to Java 17 or newer. Suppress this warning with -Dbtrace.suppressJavaDeprecationWarning=true.
```

and a matching notice on the client console when you attach. If this is expected in your
environment (a fleet you're migrating gradually, say) and you'd rather not see it, set
`-Dbtrace.suppressJavaDeprecationWarning=true` on the target JVM.

> **What just happened?** Nothing enforced — this is advance notice, not a breaking change.
> Support for Java 8–16 will be removed in BTrace's *next* major release, not this one.

## Troubleshooting

- **The migration script says "no such file"** — run it from a directory where the path you gave
  it actually resolves, or pass an absolute path.
- **`-r` refuses a directory** — that's intentional: pass `-r` explicitly to recurse
  (`scripts/migrate-btrace-script.sh -r path/to/scripts/`), so you don't accidentally rewrite a
  directory you pointed at by mistake.
- **A script still won't compile after migrating** — the migration only rewrites the package
  prefix; if your 2.x script also used APIs removed between 2.x and 3.0 (for example the
  probe-level `RequestPermission` API), you'll need to port those separately. Check
  [Migrating from 2.x to 3.0](../Migration-2.x-to-3.0.md) for the full list of what changed.

## Clean up

Nothing to detach here — this tutorial didn't attach to a running JVM. If you migrated a real
file, keep the `.bak` backup until you've confirmed the migrated script deploys and behaves as
expected.

## Go deeper

- The complete picture — coordinates, packaging, `libs=` deprecation, wire protocol, license, and
  the full Java support policy: [Migrating from 2.x to 3.0](../Migration-2.x-to-3.0.md)
- Drop the imports entirely with the flat DSL: [From Oneliner to Script](02-oneliner-to-script.md)
- Why Java < 17 is deprecated (and what BTrace itself gains from newer JDKs):
  [Instrumentation Backends](../architecture/InstrumentationBackends.md)
