# Demo 02 — Upgrade from 2.x

**Story.** You have BTrace 2.x probes. The compiled ones deploy on the 3.0 agent without being
touched: the agent rewrites `org/openjdk/btrace` references in memory when it loads the class.
Source files need their imports moved to `io.btrace`, and `scripts/migrate-btrace-script.sh`
does that for you. This demo does both against the order service from demo 01.

| File | Role |
|---|---|
| `legacy/SlowChargeProbe.java` | a 2.x probe source, `org.openjdk.btrace` imports |
| `legacy/SlowChargeProbe.class` | the same probe compiled with BTrace 2.2.6 (`btracec`) |
| `run.sh` | runs the whole story; `--keep` leaves the app running |

**By hand** (two terminals, from this directory; `btrace` on `PATH`, `JAVA_HOME` set):

```sh
java ../01-oneliner/OrderServiceApp.java          # terminal 1
btrace <PID> legacy/SlowChargeProbe.class         # terminal 2: 2.x class, untouched
cp legacy/SlowChargeProbe.java /tmp/
../../scripts/migrate-btrace-script.sh /tmp/SlowChargeProbe.java
btrace <PID> /tmp/SlowChargeProbe.java            # migrated source
```

The migration script lives in the BTrace repository (`scripts/`), not in the binary distribution.

**You should see** (numbers vary):

```
slow chargeCard: 318 ms
slow chargeCard: 364 ms
```

for the compiled class, then

```
migrated: .../SlowChargeProbe.java (backup: .../SlowChargeProbe.java.bak)
    9:import io.btrace.core.annotations.BTrace;
    ...
Done: 1 file(s) migrated (of 1 examined).
```

and the same `slow chargeCard` lines from the migrated source.

**Go deeper:** [Upgrade from 2.x in 10 Minutes](../../docs/tutorials/03-upgrade-from-2x.md),
[Migration guide](../../docs/Migration-2.x-to-3.0.md).
