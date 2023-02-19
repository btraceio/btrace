package org.openjdk.btrace.instr;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.runtime.BTraceRuntimes;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;
import org.renaissance.core.Logging;
import scala.math.BigInt;
import scala.runtime.BoxesRunTime;

import java.lang.invoke.MethodHandles;

public class ScalaBigIntTest extends InstrumentorTestBase {
    @Test
    void checkTransformation() throws Exception {
        forceIndyDispatch(true);
        try {
//            loadTargetClass("/" + Logging.class.getName().replace('.', '/') + ".class");
            loadTargetClass("/scala/math/BigInt.class");
            transform("issues/ScalaBigInteger");

            checkTransformation("", true);
        } finally {
            forceIndyDispatch(false);
        }
    }
}
