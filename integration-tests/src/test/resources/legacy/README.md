# Legacy (BTrace 2.x) probe fixtures

`LegacyOnMethodProbe.class` is the packed form (default `btracec` output, magic `0xbacecaca`) of
`LegacyOnMethodProbe.java`, produced by the genuine BTrace 2.2.6 compiler (package
`org.openjdk.btrace`):

    btracec -d out LegacyOnMethodProbe.java

Do not recompile it with the 3.0 compiler; the point is to run real 2.x output on the 3.0 agent.
`BTraceFunctionalTests.testLegacyPackedProbe` deploys it.
