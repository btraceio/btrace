package org.openjdk.btrace.extensions.builtins;

import java.util.Objects;

public final class Strings {
    public static String str(Object value) {
        return Objects.toString(value);
    }
}
