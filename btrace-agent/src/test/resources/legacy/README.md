# Legacy (BTrace 2.x) probe fixtures

Real output of the BTrace 2.2.6 compiler (package `org.openjdk.btrace`) for `SlowChargeProbe.java`:

    btracec -d out SlowChargeProbe.java            # SlowChargeProbe-2.2.6-pack.class   (packed, magic 0xbacecaca)
    btracec -nopack -d out SlowChargeProbe.java    # SlowChargeProbe-2.2.6-nopack.class (plain class, magic 0xCAFEBABE)

Used by `LegacyProbeLoadingTest` to check that 2.x probes load and verify on the 3.0 agent.
