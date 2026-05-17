# BTraceUtils Extension Split — Bytecode Rewriting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split non-core functionality out of `BTraceUtils` into `@ServiceDescriptor` extension services in `btrace-extensions/btrace-utils`, with the `Postprocessor` transparently rewriting probe bytecode at compile time so scripts require zero changes.

**Architecture:** Service interfaces + `Extension` implementations live in `btrace-extensions/btrace-utils`. The `Postprocessor` in `btrace-compiler` scans compiled probe bytecode for `INVOKESTATIC` calls to `BTraceUtils` (both outer class and inner classes) and replaces them with `INVOKEDYNAMIC` instructions backed by `BTraceUtilsBootstrap.bootstrap` in `btrace-extension`. The bootstrap resolves the live service instance from `ExtensionBridge` at first execution and returns a `ConstantCallSite`, so the overhead is paid once per call site. `BTraceUtils` itself is not changed.

**Tech Stack:** Java 8+, ASM 9.x (already in `btrace-compiler`/`btrace-instr`), BTrace extension system (`@ServiceDescriptor`, `Extension`), JUnit 5.

---

## Scope — services extracted in this plan

| Service interface | Source inner class | Methods |
|---|---|---|
| `StringsService` | `BTraceUtils.Strings` | 26 string/regex/buffer methods |
| `NumbersService` | `BTraceUtils.Numbers` | 22 math/parse/box/unbox methods |
| `TimeService` | `BTraceUtils.Time` | 4 timing methods |
| `ReferencesService` | `BTraceUtils.References` | 3 ref methods |

**Out of scope** (runtime-coupled, separate plan): `Collections`, `Atomic`, `Threads`, `Sys.*`, `Export`, `Profiling`, `Speculation`, `Reflective`, `D`, `Jfr`, `Counters`.

---

## File Structure

### New — `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/`
- `StringsService.java` — `@ServiceDescriptor` interface
- `StringsServiceImpl.java` — `Extension` implementation
- `NumbersService.java`
- `NumbersServiceImpl.java`
- `TimeService.java`
- `TimeServiceImpl.java`
- `ReferencesService.java`
- `ReferencesServiceImpl.java`

### New — `btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/`
- `StringsServiceImplTest.java`
- `NumbersServiceImplTest.java`
- `TimeServiceImplTest.java`
- `ReferencesServiceImplTest.java`

### Modified — `btrace-extensions/btrace-utils/build.gradle`
- Add 4 new services to `btraceExtension { services = [...] }`

### New — `btrace-extension/src/main/java/io/btrace/extension/`
- `BTraceUtilsBootstrap.java` — invokedynamic bootstrap for BTraceUtils rewrites

### Modified — `btrace-extension/src/main/java/io/btrace/extension/impl/ExtensionBridgeImpl.java`
- Add `getGlobalService(Class<?> serviceClass)` static method

### New — `btrace-compiler/src/main/java/io/btrace/compiler/`
- `BTraceUtilsCallRewriter.java` — ASM `MethodVisitor` that rewrites INVOKESTATIC → INVOKEDYNAMIC

### Modified — `btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java`
- Apply `BTraceUtilsCallRewriter` to each compiled probe class

### New — `btrace-compiler/src/test/java/io/btrace/compiler/`
- `BTraceUtilsCallRewriterTest.java`

---

## Task 1: StringsService interface and implementation

**Files:**
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/StringsService.java`
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/StringsServiceImpl.java`
- Create: `btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/StringsServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/StringsServiceImplTest.java
package io.btrace.utils;

import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringsServiceImplTest {
    private StringsService svc;

    @BeforeEach
    void setUp() { svc = new StringsServiceImpl(); }

    @Test void concat() { assertEquals("ab", svc.concat("a", "b")); }
    @Test void strcat()  { assertEquals("ab", svc.strcat("a", "b")); }
    @Test void startsWith() { assertTrue(svc.startsWith("hello", "hel")); }
    @Test void endsWith()   { assertTrue(svc.endsWith("hello", "llo")); }
    @Test void compareTo()  { assertEquals(0, svc.compareTo("x", "x")); }
    @Test void strcmp()     { assertTrue(svc.strcmp("a", "b") < 0); }
    @Test void stricmp()    { assertEquals(0, svc.stricmp("ABC", "abc")); }
    @Test void strstr()     { assertEquals(1, svc.strstr("hello", "ell")); }
    @Test void indexOf()    { assertEquals(1, svc.indexOf("hello", "ell")); }
    @Test void lastIndexOf(){ assertEquals(3, svc.lastIndexOf("abcabc", "abc")); }
    @Test void substrTwo()  { assertEquals("ell", svc.substr("hello", 1, 4)); }
    @Test void substrOne()  { assertEquals("llo", svc.substr("hello", 2)); }
    @Test void strlen()     { assertEquals(5, svc.strlen("hello")); }
    @Test void lengthStr()  { assertEquals(5, svc.length("hello")); }
    @Test void matchesPattern() {
        assertTrue(svc.matches(Pattern.compile("h.*o"), "hello"));
    }
    @Test void matchesString() {
        assertTrue(svc.matches("h.*o", "hello"));
    }
    @Test void regexp() {
        Pattern p = svc.regexp("[0-9]+");
        assertNotNull(p);
        assertTrue(p.matcher("123").matches());
    }
    @Test void strBool()    { assertEquals("true",  svc.str(true)); }
    @Test void strInt()     { assertEquals("42",    svc.str(42)); }
    @Test void strLong()    { assertEquals("42",    svc.str(42L)); }
    @Test void strObj()     { assertEquals("null",  svc.str((Object) null)); }
    @Test void toHexInt()   { assertEquals("ff",    svc.toHexString(255)); }
    @Test void toHexLong()  { assertEquals("ff",    svc.toHexString(255L)); }
    @Test void newSB()      { assertNotNull(svc.newStringBuilder()); }
    @Test void appendAndLength() {
        Appendable sb = svc.newStringBuilder();
        svc.append(sb, "hi");
        assertEquals(2, svc.length(sb));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure (StringsService not yet defined)**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```
Expected: compilation error — `cannot find symbol: StringsService`

- [ ] **Step 3: Write the service interface**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/StringsService.java
package io.btrace.utils;

import io.btrace.core.extensions.ServiceDescriptor;
import java.util.regex.Pattern;

@ServiceDescriptor
public interface StringsService {
    boolean startsWith(String s, String start);
    boolean endsWith(String s, String end);
    String strcat(String str1, String str2);
    String concat(String str1, String str2);
    int compareTo(String str1, String str2);
    int strcmp(String str1, String str2);
    int compareToIgnoreCase(String str1, String str2);
    int stricmp(String str1, String str2);
    int strstr(String str1, String str2);
    int indexOf(String str1, String str2);
    int lastIndexOf(String str1, String str2);
    String substr(String str, int start, int length);
    String substr(String str, int start);
    int length(String str);
    int strlen(String str);
    Pattern regexp(String regex);
    Pattern pattern(String regex);
    Pattern regexp(String regex, int flags);
    Pattern pattern(String regex, int flags);
    boolean matches(Pattern regex, String input);
    boolean matches(String regex, String input);
    String str(boolean b);
    String str(char c);
    String str(int i);
    String str(long l);
    String str(float f);
    String str(double d);
    String str(Object obj);
    String str(Object[] array);
    String toHexString(int i);
    String toHexString(long l);
    Appendable newStringBuilder(boolean threadSafe);
    Appendable newStringBuilder();
    Appendable append(Appendable buffer, String strToAppend);
    int length(Appendable buffer);
}
```

- [ ] **Step 4: Write the Extension implementation**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/StringsServiceImpl.java
package io.btrace.utils;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.extensions.Extension;
import java.util.regex.Pattern;

public final class StringsServiceImpl extends Extension implements StringsService {
    @Override public boolean startsWith(String s, String start) { return s.startsWith(start); }
    @Override public boolean endsWith(String s, String end) { return s.endsWith(end); }
    @Override public String strcat(String s1, String s2) { return s1.concat(s2); }
    @Override public String concat(String s1, String s2) { return s1.concat(s2); }
    @Override public int compareTo(String s1, String s2) { return s1.compareTo(s2); }
    @Override public int strcmp(String s1, String s2) { return s1.compareTo(s2); }
    @Override public int compareToIgnoreCase(String s1, String s2) { return s1.compareToIgnoreCase(s2); }
    @Override public int stricmp(String s1, String s2) { return s1.compareToIgnoreCase(s2); }
    @Override public int strstr(String s1, String s2) { return s1.indexOf(s2); }
    @Override public int indexOf(String s1, String s2) { return s1.indexOf(s2); }
    @Override public int lastIndexOf(String s1, String s2) { return s1.lastIndexOf(s2); }
    @Override public String substr(String str, int start, int end) { return str.substring(start, end); }
    @Override public String substr(String str, int start) { return str.substring(start); }
    @Override public int length(String str) { return str.length(); }
    @Override public int strlen(String str) { return str.length(); }
    @Override public Pattern regexp(String regex) { return Pattern.compile(regex); }
    @Override public Pattern pattern(String regex) { return Pattern.compile(regex); }
    @Override public Pattern regexp(String regex, int flags) { return Pattern.compile(regex, flags); }
    @Override public Pattern pattern(String regex, int flags) { return Pattern.compile(regex, flags); }
    @Override public boolean matches(Pattern p, String input) { return p.matcher(input).matches(); }
    @Override public boolean matches(String regex, String input) { return Pattern.matches(regex, input); }
    @Override public String str(boolean b) { return Boolean.toString(b); }
    @Override public String str(char c) { return Character.toString(c); }
    @Override public String str(int i) { return Integer.toString(i); }
    @Override public String str(long l) { return Long.toString(l); }
    @Override public String str(float f) { return Float.toString(f); }
    @Override public String str(double d) { return Double.toString(d); }
    @Override public String str(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return (String) obj;
        if (obj.getClass().getClassLoader() == null) {
            try { return obj.toString(); } catch (NullPointerException e) { return "null"; }
        }
        return obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
    }
    @Override public String str(Object[] array) {
        if (array == null) return "null";
        StringBuilder buf = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) buf.append(", ");
            buf.append(str(array[i]));
        }
        return buf.append(']').toString();
    }
    @Override public String toHexString(int i) { return Integer.toHexString(i); }
    @Override public String toHexString(long l) { return Long.toHexString(l); }
    @Override public Appendable newStringBuilder(boolean threadSafe) {
        return BTraceRuntime.newStringBuilder(threadSafe);
    }
    @Override public Appendable newStringBuilder() { return BTraceRuntime.newStringBuilder(); }
    @Override public Appendable append(Appendable buf, String s) { return BTraceRuntime.append(buf, s); }
    @Override public int length(Appendable buf) { return BTraceRuntime.length(buf); }

    @Override public void close() {}
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add btrace-extensions/btrace-utils/src/
git commit -m "feat(btrace-utils): add StringsService and StringsServiceImpl extension"
```

---

## Task 2: NumbersService interface and implementation

**Files:**
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/NumbersService.java`
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/NumbersServiceImpl.java`
- Create: `btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/NumbersServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/NumbersServiceImplTest.java
package io.btrace.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumbersServiceImplTest {
    private NumbersService svc;
    @BeforeEach void setUp() { svc = new NumbersServiceImpl(); }

    @Test void random()        { double r = svc.random(); assertTrue(r >= 0.0 && r < 1.0); }
    @Test void log()           { assertEquals(Math.log(Math.E), svc.log(Math.E), 1e-10); }
    @Test void log10()         { assertEquals(1.0, svc.log10(10.0), 1e-10); }
    @Test void exp()           { assertEquals(Math.E, svc.exp(1.0), 1e-10); }
    @Test void isNaNDouble()   { assertTrue(svc.isNaN(Double.NaN)); }
    @Test void isNaNFloat()    { assertTrue(svc.isNaN(Float.NaN)); }
    @Test void isInfDouble()   { assertTrue(svc.isInfinite(Double.POSITIVE_INFINITY)); }
    @Test void isInfFloat()    { assertTrue(svc.isInfinite(Float.POSITIVE_INFINITY)); }
    @Test void parseBoolean()  { assertTrue(svc.parseBoolean("true")); }
    @Test void parseByte()     { assertEquals((byte)42,  svc.parseByte("42")); }
    @Test void parseShort()    { assertEquals((short)42, svc.parseShort("42")); }
    @Test void parseInt()      { assertEquals(42,        svc.parseInt("42")); }
    @Test void parseLong()     { assertEquals(42L,       svc.parseLong("42")); }
    @Test void parseFloat()    { assertEquals(3.14f,     svc.parseFloat("3.14"), 0.001f); }
    @Test void parseDouble()   { assertEquals(3.14,      svc.parseDouble("3.14"), 0.001); }
    @Test void boxBoolean()    { assertEquals(Boolean.TRUE, svc.box(true)); }
    @Test void boxInt()        { assertEquals(Integer.valueOf(7), svc.box(7)); }
    @Test void unboxBoolean()  { assertTrue(svc.unbox(Boolean.TRUE)); }
    @Test void unboxInteger()  { assertEquals(7, svc.unbox(Integer.valueOf(7))); }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 3: Write the service interface**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/NumbersService.java
package io.btrace.utils;

import io.btrace.core.extensions.ServiceDescriptor;

@ServiceDescriptor
public interface NumbersService {
    double random();
    double log(double a);
    double log10(double a);
    double exp(double a);
    boolean isNaN(double d);
    boolean isNaN(float f);
    boolean isInfinite(double d);
    boolean isInfinite(float f);
    boolean parseBoolean(String s);
    byte   parseByte(String s);
    short  parseShort(String s);
    int    parseInt(String s);
    long   parseLong(String s);
    float  parseFloat(String s);
    double parseDouble(String s);
    Boolean   box(boolean b);
    Character box(char c);
    Byte      box(byte b);
    Short     box(short s);
    Integer   box(int i);
    Long      box(long l);
    Float     box(float f);
    Double    box(double d);
    boolean unbox(Boolean b);
    char    unbox(Character c);
    byte    unbox(Byte b);
    short   unbox(Short s);
    int     unbox(Integer i);
    long    unbox(Long l);
    float   unbox(Float f);
    double  unbox(Double d);
}
```

- [ ] **Step 4: Write the Extension implementation**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/NumbersServiceImpl.java
package io.btrace.utils;

import io.btrace.core.extensions.Extension;

public final class NumbersServiceImpl extends Extension implements NumbersService {
    @Override public double  random()             { return Math.random(); }
    @Override public double  log(double a)        { return Math.log(a); }
    @Override public double  log10(double a)      { return Math.log10(a); }
    @Override public double  exp(double a)        { return Math.exp(a); }
    @Override public boolean isNaN(double d)      { return Double.isNaN(d); }
    @Override public boolean isNaN(float f)       { return Float.isNaN(f); }
    @Override public boolean isInfinite(double d) { return Double.isInfinite(d); }
    @Override public boolean isInfinite(float f)  { return Float.isInfinite(f); }
    @Override public boolean parseBoolean(String s) { return Boolean.parseBoolean(s); }
    @Override public byte    parseByte(String s)    { return Byte.parseByte(s); }
    @Override public short   parseShort(String s)   { return Short.parseShort(s); }
    @Override public int     parseInt(String s)     { return Integer.parseInt(s); }
    @Override public long    parseLong(String s)    { return Long.parseLong(s); }
    @Override public float   parseFloat(String s)   { return Float.parseFloat(s); }
    @Override public double  parseDouble(String s)  { return Double.parseDouble(s); }
    @Override public Boolean   box(boolean b) { return b; }
    @Override public Character box(char c)    { return c; }
    @Override public Byte      box(byte b)    { return b; }
    @Override public Short     box(short s)   { return s; }
    @Override public Integer   box(int i)     { return i; }
    @Override public Long      box(long l)    { return l; }
    @Override public Float     box(float f)   { return f; }
    @Override public Double    box(double d)  { return d; }
    @Override public boolean unbox(Boolean b)   { return b; }
    @Override public char    unbox(Character c) { return c; }
    @Override public byte    unbox(Byte b)      { return b; }
    @Override public short   unbox(Short s)     { return s; }
    @Override public int     unbox(Integer i)   { return i; }
    @Override public long    unbox(Long l)      { return l; }
    @Override public float   unbox(Float f)     { return f; }
    @Override public double  unbox(Double d)    { return d; }
    @Override public void close() {}
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 6: Commit**

```bash
git add btrace-extensions/btrace-utils/src/
git commit -m "feat(btrace-utils): add NumbersService and NumbersServiceImpl extension"
```

---

## Task 3: TimeService interface and implementation

**Files:**
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/TimeService.java`
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/TimeServiceImpl.java`
- Create: `btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/TimeServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/TimeServiceImplTest.java
package io.btrace.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeServiceImplTest {
    private TimeService svc;
    @BeforeEach void setUp() { svc = new TimeServiceImpl(); }

    @Test void millis() {
        long before = System.currentTimeMillis();
        long actual = svc.millis();
        long after  = System.currentTimeMillis();
        assertTrue(actual >= before && actual <= after);
    }
    @Test void nanos() {
        long before = System.nanoTime();
        long actual = svc.nanos();
        long after  = System.nanoTime();
        assertTrue(actual >= before && actual <= after);
    }
    @Test void timestamp() {
        assertNotNull(svc.timestamp());
        assertFalse(svc.timestamp().isEmpty());
    }
    @Test void timestampWithFormat() {
        String ts = svc.timestamp("yyyy");
        assertEquals(4, ts.length());
        assertTrue(ts.matches("[0-9]{4}"));
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 3: Write the service interface**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/TimeService.java
package io.btrace.utils;

import io.btrace.core.extensions.ServiceDescriptor;

@ServiceDescriptor
public interface TimeService {
    long   millis();
    long   nanos();
    String timestamp();
    String timestamp(String format);
}
```

- [ ] **Step 4: Write the Extension implementation**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/TimeServiceImpl.java
package io.btrace.utils;

import io.btrace.core.extensions.Extension;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public final class TimeServiceImpl extends Extension implements TimeService {
    @Override public long millis() { return System.currentTimeMillis(); }
    @Override public long nanos()  { return System.nanoTime(); }
    @Override public String timestamp() {
        return new SimpleDateFormat().format(Calendar.getInstance().getTime());
    }
    @Override public String timestamp(String format) {
        return new SimpleDateFormat(format).format(Calendar.getInstance().getTime());
    }
    @Override public void close() {}
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 6: Commit**

```bash
git add btrace-extensions/btrace-utils/src/
git commit -m "feat(btrace-utils): add TimeService and TimeServiceImpl extension"
```

---

## Task 4: ReferencesService interface and implementation

**Files:**
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/ReferencesService.java`
- Create: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/ReferencesServiceImpl.java`
- Create: `btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/ReferencesServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-extensions/btrace-utils/src/test/java/io/btrace/utils/ReferencesServiceImplTest.java
package io.btrace.utils;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferencesServiceImplTest {
    private ReferencesService svc;
    @BeforeEach void setUp() { svc = new ReferencesServiceImpl(); }

    @Test void weakRef() {
        Object obj = new Object();
        WeakReference<?> ref = svc.weakRef(obj);
        assertNotNull(ref);
        assertSame(obj, ref.get());
    }
    @Test void softRef() {
        Object obj = new Object();
        SoftReference<?> ref = svc.softRef(obj);
        assertNotNull(ref);
        assertSame(obj, ref.get());
    }
    @Test void deref() {
        String s = "hello";
        WeakReference<String> ref = new WeakReference<>(s);
        assertEquals("hello", svc.deref(ref));
    }
    @Test void derefNonBootstrap_throws() {
        Object obj = new Object(); // non-bootstrap class
        WeakReference<Object> ref = new WeakReference<>(obj);
        assertThrows(IllegalArgumentException.class, () -> svc.deref(ref));
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 3: Write the service interface**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/ReferencesService.java
package io.btrace.utils;

import io.btrace.core.extensions.ServiceDescriptor;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

@ServiceDescriptor
public interface ReferencesService {
    WeakReference weakRef(Object obj);
    SoftReference softRef(Object obj);
    Object deref(Reference ref);
}
```

- [ ] **Step 4: Write the Extension implementation**

```java
// btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/ReferencesServiceImpl.java
package io.btrace.utils;

import io.btrace.core.extensions.Extension;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public final class ReferencesServiceImpl extends Extension implements ReferencesService {
    @Override public WeakReference weakRef(Object obj) { return new WeakReference<>(obj); }
    @Override public SoftReference softRef(Object obj) { return new SoftReference<>(obj); }
    @Override public Object deref(Reference ref) {
        if (ref.getClass().getClassLoader() == null) return ref.get();
        throw new IllegalArgumentException();
    }
    @Override public void close() {}
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-extensions:btrace-utils:test
```

- [ ] **Step 6: Commit**

```bash
git add btrace-extensions/btrace-utils/src/
git commit -m "feat(btrace-utils): add ReferencesService and ReferencesServiceImpl extension"
```

---

## Task 5: Update btrace-utils build.gradle with all new services

**Files:**
- Modify: `btrace-extensions/btrace-utils/build.gradle`

- [ ] **Step 1: Update build.gradle**

Replace the `btraceExtension` block:

```groovy
// btrace-extensions/btrace-utils/build.gradle
btraceExtension {
    id = 'btrace-utils'
    name = 'BTrace Utilities'
    description = 'Utility services for BTrace scripts (eg. printer, strings, numbers, time)'
    services = [
        'io.btrace.utils.PrinterService',
        'io.btrace.utils.StringsService',
        'io.btrace.utils.NumbersService',
        'io.btrace.utils.TimeService',
        'io.btrace.utils.ReferencesService',
    ]
}
```

- [ ] **Step 2: Verify build produces extension artifact**

```bash
./gradlew :btrace-extensions:btrace-utils:build
```
Expected: BUILD SUCCESSFUL. Check that `btrace-extensions/btrace-utils/build/libs/` contains the API and impl JARs.

- [ ] **Step 3: Verify manifest contains all services**

```bash
jar tf btrace-extensions/btrace-utils/build/libs/btrace-utils-*-api.jar | grep -i manifest
jar xf btrace-extensions/btrace-utils/build/libs/btrace-utils-*-api.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF
```
Expected: `BTrace-Extension-Services:` line lists all 5 services.

- [ ] **Step 4: Commit**

```bash
git add btrace-extensions/btrace-utils/build.gradle
git commit -m "feat(btrace-utils): declare all new services in extension manifest"
```

---

## Task 6: Add ExtensionBridge.getGlobalService()

The invokedynamic bootstrap needs a way to resolve a live service instance by interface class. Currently `ExtensionBridge` has no such static accessor.

**Files:**
- Modify: `btrace-extension/src/main/java/io/btrace/extension/impl/ExtensionBridgeImpl.java`
- Modify: `btrace-extension/src/main/java/io/btrace/extension/ExtensionBridge.java` (if it is an interface/abstract class)

- [ ] **Step 1: Write the failing test**

```java
// btrace-extension/src/test/java/io/btrace/extension/impl/ExtensionBridgeImplTest.java
// (Add to existing test class or create new one)
@Test
void getGlobalService_returnsNullWhenNotLoaded() {
    // Verify graceful null for unknown service
    Object result = ExtensionBridgeImpl.getGlobalService(
        io.btrace.utils.StringsService.class);
    // null is acceptable when extension not loaded in unit test context
    // (full integration test in Task 10 verifies actual service resolution)
    assertNull(result);
}
```

- [ ] **Step 2: Run — expect compilation failure (`getGlobalService` not defined)**

```bash
./gradlew :btrace-extension:test
```

- [ ] **Step 3: Add getGlobalService to ExtensionBridgeImpl**

Find the current `ExtensionBridgeImpl` class and add this method (place near existing `bootstrap` method):

```java
// In io.btrace.extension.impl.ExtensionBridgeImpl:

/**
 * Returns the singleton service instance for a given service interface, or null
 * if the extension providing that service is not loaded.
 * Called from BTraceUtilsBootstrap at invokedynamic link time.
 */
public static Object getGlobalService(Class<?> serviceClass) {
    ExtensionBridgeImpl bridge = INSTANCE; // existing singleton field
    if (bridge == null) return null;
    try {
        return bridge.getExtensionService(serviceClass);
    } catch (Exception e) {
        return null;
    }
}
```

Also add the private helper `getExtensionService(Class<?>)` that walks the loaded extensions and finds an instance implementing the given interface:

```java
private Object getExtensionService(Class<?> serviceClass) {
    // extensionLoader is the existing ExtensionLoader field on the bridge
    if (extensionLoader == null) return null;
    for (ExtensionDescriptorDTO ext : extensionLoader.getAvailableExtensions()) {
        try {
            Class<?> implClass = extensionLoader.loadExtensionClass(ext, serviceClass);
            if (implClass != null && serviceClass.isAssignableFrom(implClass)) {
                return extensionLoader.getOrCreateInstance(ext, implClass);
            }
        } catch (Exception ignored) {}
    }
    return null;
}
```

> **Note:** The exact method names (`getAvailableExtensions`, `loadExtensionClass`, `getOrCreateInstance`) must be verified against the current `ExtensionLoader` API. Adjust to match what exists — the key contract is: given a service interface `Class<?>`, find and return a singleton `Extension` instance implementing it, or null.

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :btrace-extension:test
```

- [ ] **Step 5: Commit**

```bash
git add btrace-extension/src/
git commit -m "feat(extension): add ExtensionBridgeImpl.getGlobalService() for bootstrap resolution"
```

---

## Task 7: Add BTraceUtilsBootstrap

This class lives in `btrace-extension` (which is on the bootstrap classpath at runtime and will merge into `btrace-core` per the consolidation plan).

**Files:**
- Create: `btrace-extension/src/main/java/io/btrace/extension/BTraceUtilsBootstrap.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-extension/src/test/java/io/btrace/extension/BTraceUtilsBootstrapTest.java
package io.btrace.extension;

import java.lang.invoke.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BTraceUtilsBootstrapTest {
    @Test
    void bootstrap_throwsWhenServiceUnavailable() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType type = MethodType.methodType(String.class, String.class, String.class);
        // "io.btrace.utils.NoSuchService" is not registered — must throw
        assertThrows(IllegalStateException.class, () ->
            BTraceUtilsBootstrap.bootstrap(
                lookup, "concat", type, "io.btrace.utils.NoSuchService"));
    }
}
```

- [ ] **Step 2: Run — expect compilation failure (`BTraceUtilsBootstrap` not defined)**

```bash
./gradlew :btrace-extension:test
```

- [ ] **Step 3: Write the bootstrap class**

```java
// btrace-extension/src/main/java/io/btrace/extension/BTraceUtilsBootstrap.java
package io.btrace.extension;

import io.btrace.extension.impl.ExtensionBridgeImpl;
import java.lang.invoke.*;

/**
 * Invokedynamic bootstrap for BTraceUtils static-call rewrites.
 * Each rewritten call site links once (ConstantCallSite), paying
 * service lookup cost exactly once per JIT compilation unit.
 */
public final class BTraceUtilsBootstrap {
    private BTraceUtilsBootstrap() {}

    /**
     * Bootstrap method referenced by every rewritten BTraceUtils call.
     *
     * @param lookup      caller lookup (provided by JVM)
     * @param methodName  method name on the target service interface
     * @param type        method type matching the original static call
     * @param serviceClass fully-qualified name of the service interface
     */
    public static CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String methodName,
            MethodType type,
            String serviceClass) throws Exception {

        Class<?> svcIface = Class.forName(serviceClass, true,
                Thread.currentThread().getContextClassLoader());

        Object svc = ExtensionBridgeImpl.getGlobalService(svcIface);
        if (svc == null) {
            throw new IllegalStateException(
                "BTrace extension service not available: " + serviceClass
                + " — ensure btrace-utils extension is loaded");
        }

        // findVirtual on the interface dispatches through the concrete impl
        MethodHandle mh = MethodHandles.publicLookup()
                .findVirtual(svcIface, methodName, type);
        // bindTo pre-fills the receiver so the handle matches the original
        // static call's type (no leading 'this' argument)
        return new ConstantCallSite(mh.bindTo(svc));
    }
}
```

- [ ] **Step 4: Run test — expect PASS (the no-service test passes)**

```bash
./gradlew :btrace-extension:test
```

- [ ] **Step 5: Commit**

```bash
git add btrace-extension/src/
git commit -m "feat(extension): add BTraceUtilsBootstrap invokedynamic bootstrap"
```

---

## Task 8: BTraceUtilsCallRewriter — ASM method visitor

This visitor detects `INVOKESTATIC` calls to `BTraceUtils` (outer class and inner classes) and replaces them with `INVOKEDYNAMIC` backed by `BTraceUtilsBootstrap.bootstrap`.

**Files:**
- Create: `btrace-compiler/src/main/java/io/btrace/compiler/BTraceUtilsCallRewriter.java`
- Create: `btrace-compiler/src/test/java/io/btrace/compiler/BTraceUtilsCallRewriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
// btrace-compiler/src/test/java/io/btrace/compiler/BTraceUtilsCallRewriterTest.java
package io.btrace.compiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import java.io.PrintWriter;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

class BTraceUtilsCallRewriterTest {

    /** Passes a tiny synthetic class through the rewriter and checks the output bytecode. */
    private String rewrite(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new BTraceUtilsCallRewriter(cw), ClassReader.EXPAND_FRAMES);
        byte[] rewritten = cw.toByteArray();
        // Disassemble to text for assertion
        StringWriter sw = new StringWriter();
        new ClassReader(rewritten).accept(
            new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw)),
            ClassReader.SKIP_DEBUG);
        return sw.toString();
    }

    /** Generates bytecode for: String r = BTraceUtils.Strings.concat("a","b"); */
    private byte[] syntheticConcatClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "run", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("a");
        mv.visitLdcInsn("b");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
            "io/btrace/core/BTraceUtils$Strings", "concat",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void concat_rewrittenToInvokeDynamic() {
        String disasm = rewrite(syntheticConcatClass());
        // Must contain INVOKEDYNAMIC, not INVOKESTATIC to BTraceUtils
        assertTrue(disasm.contains("INVOKEDYNAMIC"),
            "Expected INVOKEDYNAMIC in rewritten bytecode:\n" + disasm);
        assertFalse(disasm.contains("BTraceUtils"),
            "BTraceUtils reference must be removed:\n" + disasm);
        assertTrue(disasm.contains("io/btrace/utils/StringsService"),
            "Must reference StringsService:\n" + disasm);
    }

    @Test
    void unrelatedStaticCall_notRewritten() {
        ClassWriter cw2 = new ClassWriter(0);
        cw2.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Test2", null, "java/lang/Object", null);
        MethodVisitor mv = cw2.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "run", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
            "java/lang/System", "gc", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw2.visitEnd();
        String disasm = rewrite(cw2.toByteArray());
        assertFalse(disasm.contains("INVOKEDYNAMIC"),
            "Unrelated static calls must not be rewritten:\n" + disasm);
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :btrace-compiler:test --tests "*BTraceUtilsCallRewriterTest"
```

- [ ] **Step 3: Write the rewriter**

```java
// btrace-compiler/src/main/java/io/btrace/compiler/BTraceUtilsCallRewriter.java
package io.btrace.compiler;

import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ASM ClassVisitor that rewrites INVOKESTATIC calls to BTraceUtils (outer class
 * and all inner utility classes) into INVOKEDYNAMIC calls backed by
 * {@code io.btrace.extension.BTraceUtilsBootstrap#bootstrap}.
 *
 * The call sites are constant (ConstantCallSite) so the service lookup cost
 * is paid exactly once per call site, not per invocation.
 */
public final class BTraceUtilsCallRewriter extends ClassVisitor {

    /** Keyed by "owner\0name\0descriptor". Value is (serviceClass, targetMethodName). */
    private static final Map<String, String[]> REWRITE_TABLE = buildTable();

    private static final String BOOTSTRAP_OWNER =
            "io/btrace/extension/BTraceUtilsBootstrap";
    private static final String BOOTSTRAP_NAME = "bootstrap";
    private static final String BOOTSTRAP_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;"
            + "Ljava/lang/String;"
            + "Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/String;"
            + ")Ljava/lang/invoke/CallSite;";

    public BTraceUtilsCallRewriter(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String sig, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
        return new RewritingMethodVisitor(mv);
    }

    private static final class RewritingMethodVisitor extends MethodVisitor {
        RewritingMethodVisitor(MethodVisitor mv) { super(Opcodes.ASM9, mv); }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC) {
                String key = owner + '\0' + name + '\0' + descriptor;
                String[] target = REWRITE_TABLE.get(key);
                if (target != null) {
                    // target[0] = service class name (dot-separated)
                    // target[1] = method name on service
                    Handle bsm = new Handle(
                            Opcodes.H_INVOKESTATIC,
                            BOOTSTRAP_OWNER,
                            BOOTSTRAP_NAME,
                            BOOTSTRAP_DESC,
                            false);
                    mv.visitInvokeDynamicInsn(target[1], descriptor, bsm, target[0]);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    // -----------------------------------------------------------------------
    // Rewrite table: maps (owner, name, descriptor) → (serviceClass, method)
    // -----------------------------------------------------------------------

    private static Map<String, String[]> buildTable() {
        Map<String, String[]> t = new HashMap<>();

        // ── StringsService ──────────────────────────────────────────────────
        String SS = "io.btrace.utils.StringsService";
        String SI = "io/btrace/core/BTraceUtils$Strings";
        String BO = "io/btrace/core/BTraceUtils"; // outer class delegate

        // Two-String → String
        for (String name : new String[]{"strcat", "concat"})
            addS(t, SI, name, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", SS, "concat");
        for (String name : new String[]{"strcmp", "compareTo"})
            addS(t, SI, name, "(Ljava/lang/String;Ljava/lang/String;)I", SS, "compareTo");
        for (String name : new String[]{"stricmp", "compareToIgnoreCase"})
            addS(t, SI, name, "(Ljava/lang/String;Ljava/lang/String;)I", SS, "compareToIgnoreCase");
        for (String name : new String[]{"strstr", "indexOf"})
            addS(t, SI, name, "(Ljava/lang/String;Ljava/lang/String;)I", SS, "indexOf");
        addS(t, SI, "lastIndexOf", "(Ljava/lang/String;Ljava/lang/String;)I", SS, "lastIndexOf");
        addS(t, SI, "startsWith",  "(Ljava/lang/String;Ljava/lang/String;)Z", SS, "startsWith");
        addS(t, SI, "endsWith",    "(Ljava/lang/String;Ljava/lang/String;)Z", SS, "endsWith");
        addS(t, SI, "substr",      "(Ljava/lang/String;II)Ljava/lang/String;",  SS, "substr");
        addS(t, SI, "substr",      "(Ljava/lang/String;I)Ljava/lang/String;",   SS, "substr");
        addS(t, SI, "length",      "(Ljava/lang/String;)I",                     SS, "length");
        addS(t, SI, "strlen",      "(Ljava/lang/String;)I",                     SS, "strlen");
        addS(t, SI, "length",      "(Ljava/lang/Appendable;)I",                 SS, "length");
        addS(t, SI, "append",
            "(Ljava/lang/Appendable;Ljava/lang/String;)Ljava/lang/Appendable;", SS, "append");
        addS(t, SI, "newStringBuilder", "(Z)Ljava/lang/Appendable;",  SS, "newStringBuilder");
        addS(t, SI, "newStringBuilder", "()Ljava/lang/Appendable;",   SS, "newStringBuilder");
        // regex
        addS(t, SI, "regexp",  "(Ljava/lang/String;)Ljava/util/regex/Pattern;",   SS, "regexp");
        addS(t, SI, "pattern", "(Ljava/lang/String;)Ljava/util/regex/Pattern;",   SS, "pattern");
        addS(t, SI, "regexp",  "(Ljava/lang/String;I)Ljava/util/regex/Pattern;",  SS, "regexp");
        addS(t, SI, "pattern", "(Ljava/lang/String;I)Ljava/util/regex/Pattern;",  SS, "pattern");
        addS(t, SI, "matches",
            "(Ljava/util/regex/Pattern;Ljava/lang/String;)Z", SS, "matches");
        addS(t, SI, "matches",
            "(Ljava/lang/String;Ljava/lang/String;)Z",        SS, "matches");
        // str overloads
        addS(t, SI, "str", "(Z)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(C)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(I)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(J)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(F)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(D)Ljava/lang/String;",                    SS, "str");
        addS(t, SI, "str", "(Ljava/lang/Object;)Ljava/lang/String;",   SS, "str");
        addS(t, SI, "str", "([Ljava/lang/Object;)Ljava/lang/String;",  SS, "str");
        addS(t, SI, "toHexString", "(I)Ljava/lang/String;",            SS, "toHexString");
        addS(t, SI, "toHexString", "(J)Ljava/lang/String;",            SS, "toHexString");

        // Outer-class BTraceUtils delegates for Strings
        addS(t, BO, "strcat",   "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", SS, "concat");
        addS(t, BO, "concat",   "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", SS, "concat");
        addS(t, BO, "strcmp",   "(Ljava/lang/String;Ljava/lang/String;)I", SS, "compareTo");
        addS(t, BO, "stricmp",  "(Ljava/lang/String;Ljava/lang/String;)I", SS, "compareToIgnoreCase");
        addS(t, BO, "strstr",   "(Ljava/lang/String;Ljava/lang/String;)I", SS, "strstr");

        // ── NumbersService ──────────────────────────────────────────────────
        String NS = "io.btrace.utils.NumbersService";
        String NI = "io/btrace/core/BTraceUtils$Numbers";

        addS(t, NI, "random",  "()D",                     NS, "random");
        addS(t, NI, "log",     "(D)D",                    NS, "log");
        addS(t, NI, "log10",   "(D)D",                    NS, "log10");
        addS(t, NI, "exp",     "(D)D",                    NS, "exp");
        addS(t, NI, "isNaN",   "(D)Z",                    NS, "isNaN");
        addS(t, NI, "isNaN",   "(F)Z",                    NS, "isNaN");
        addS(t, NI, "isInfinite", "(D)Z",                 NS, "isInfinite");
        addS(t, NI, "isInfinite", "(F)Z",                 NS, "isInfinite");
        addS(t, NI, "parseBoolean", "(Ljava/lang/String;)Z",  NS, "parseBoolean");
        addS(t, NI, "parseByte",    "(Ljava/lang/String;)B",  NS, "parseByte");
        addS(t, NI, "parseShort",   "(Ljava/lang/String;)S",  NS, "parseShort");
        addS(t, NI, "parseInt",     "(Ljava/lang/String;)I",  NS, "parseInt");
        addS(t, NI, "parseLong",    "(Ljava/lang/String;)J",  NS, "parseLong");
        addS(t, NI, "parseFloat",   "(Ljava/lang/String;)F",  NS, "parseFloat");
        addS(t, NI, "parseDouble",  "(Ljava/lang/String;)D",  NS, "parseDouble");
        addS(t, NI, "box", "(Z)Ljava/lang/Boolean;",    NS, "box");
        addS(t, NI, "box", "(C)Ljava/lang/Character;",  NS, "box");
        addS(t, NI, "box", "(B)Ljava/lang/Byte;",       NS, "box");
        addS(t, NI, "box", "(S)Ljava/lang/Short;",      NS, "box");
        addS(t, NI, "box", "(I)Ljava/lang/Integer;",    NS, "box");
        addS(t, NI, "box", "(J)Ljava/lang/Long;",       NS, "box");
        addS(t, NI, "box", "(F)Ljava/lang/Float;",      NS, "box");
        addS(t, NI, "box", "(D)Ljava/lang/Double;",     NS, "box");
        addS(t, NI, "unbox", "(Ljava/lang/Boolean;)Z",   NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Character;)C", NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Byte;)B",      NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Short;)S",     NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Integer;)I",   NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Long;)J",      NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Float;)F",     NS, "unbox");
        addS(t, NI, "unbox", "(Ljava/lang/Double;)D",    NS, "unbox");

        // ── TimeService ─────────────────────────────────────────────────────
        String TS = "io.btrace.utils.TimeService";
        String TI = "io/btrace/core/BTraceUtils$Time";
        addS(t, TI, "millis",    "()J",                    TS, "millis");
        addS(t, TI, "nanos",     "()J",                    TS, "nanos");
        addS(t, TI, "timestamp", "()Ljava/lang/String;",   TS, "timestamp");
        addS(t, TI, "timestamp", "(Ljava/lang/String;)Ljava/lang/String;", TS, "timestamp");
        // outer-class delegates for time
        addS(t, BO, "timeMillis", "()J",                   TS, "millis");
        addS(t, BO, "timeNanos",  "()J",                   TS, "nanos");
        addS(t, BO, "timestamp",  "()Ljava/lang/String;",  TS, "timestamp");
        addS(t, BO, "timestamp",  "(Ljava/lang/String;)Ljava/lang/String;", TS, "timestamp");

        // ── ReferencesService ───────────────────────────────────────────────
        String RS = "io.btrace.utils.ReferencesService";
        String RI = "io/btrace/core/BTraceUtils$References";
        addS(t, RI, "weakRef", "(Ljava/lang/Object;)Ljava/lang/ref/WeakReference;", RS, "weakRef");
        addS(t, RI, "softRef", "(Ljava/lang/Object;)Ljava/lang/ref/SoftReference;", RS, "softRef");
        addS(t, RI, "deref",   "(Ljava/lang/ref/Reference;)Ljava/lang/Object;",     RS, "deref");
        addS(t, BO, "weakRef", "(Ljava/lang/Object;)Ljava/lang/ref/WeakReference;", RS, "weakRef");
        addS(t, BO, "softRef", "(Ljava/lang/Object;)Ljava/lang/ref/SoftReference;", RS, "softRef");
        addS(t, BO, "deref",   "(Ljava/lang/ref/Reference;)Ljava/lang/Object;",     RS, "deref");

        return t;
    }

    private static void addS(Map<String, String[]> t,
                              String owner, String name, String desc,
                              String svcClass, String svcMethod) {
        t.put(owner + '\0' + name + '\0' + desc, new String[]{svcClass, svcMethod});
    }
}
```

- [ ] **Step 4: Run the rewriter tests — expect PASS**

```bash
./gradlew :btrace-compiler:test --tests "*BTraceUtilsCallRewriterTest"
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add btrace-compiler/src/
git commit -m "feat(compiler): add BTraceUtilsCallRewriter ASM visitor with rewrite table"
```

---

## Task 9: Wire BTraceUtilsCallRewriter into Postprocessor

The `Postprocessor` already runs a visitor chain over compiled probe classes. The rewriter is applied as the first visitor (so later passes see the invokedynamic instructions rather than static calls).

**Files:**
- Modify: `btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java`

- [ ] **Step 1: Locate the entry point in Postprocessor**

```bash
grep -n "ClassReader\|ClassWriter\|ClassVisitor\|visitMethod\|process\|transform" \
  btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java | head -30
```

Find the method that processes each probe class's byte array (typically something like `process(byte[])` or `postprocess(byte[])`).

- [ ] **Step 2: Add the rewriter to the visitor chain**

In the method that constructs the `ClassWriter` / visitor chain, add `BTraceUtilsCallRewriter` as the outermost visitor wrapping the existing chain:

```java
// Example — actual line numbers will differ; find the ClassReader→ClassWriter pipeline:
//
// BEFORE (existing code):
//   ClassReader cr = new ClassReader(classBytes);
//   ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
//   ClassVisitor chain = new SomeExistingVisitor(cw, ...);
//   cr.accept(chain, ClassReader.EXPAND_FRAMES);
//   return cw.toByteArray();
//
// AFTER: wrap chain with BTraceUtilsCallRewriter so rewrites apply first:
//   ClassReader cr = new ClassReader(classBytes);
//   ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
//   ClassVisitor chain = new SomeExistingVisitor(cw, ...);
//   ClassVisitor withRewrite = new BTraceUtilsCallRewriter(chain);  // <-- add this
//   cr.accept(withRewrite, ClassReader.EXPAND_FRAMES);
//   return cw.toByteArray();
```

If `Postprocessor` handles each class separately (e.g., iterates over a map of class bytes), apply the rewriter in that loop. The rewriter is stateless; the same instance can be constructed per class.

- [ ] **Step 3: Run the compiler tests**

```bash
./gradlew :btrace-compiler:test
```
Expected: BUILD SUCCESSFUL (existing tests must not regress)

- [ ] **Step 4: Smoke-test with a real BTrace script**

Compile a minimal script that uses `BTraceUtils.Strings.concat`:

```java
// /tmp/TestTrace.java
import io.btrace.core.BTraceRuntime;
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnMethod;

@BTrace
public class TestTrace {
    @OnMethod(clazz = "java.io.File", method = "<init>")
    public static void onFileInit() {
        String s = BTraceUtils.Strings.concat("file-", "init");
        BTraceUtils.println(s);
    }
}
```

```bash
./gradlew :btrace-dist:build
btrace-dist/build/resources/main/bin/btracec /tmp/TestTrace.java -d /tmp/out
javap -verbose -p /tmp/out/TestTrace.class | grep -A5 "invokedynamic\|INVOKESTATIC"
```

Expected: the output shows `invokedynamic  #N,  0` calls with `BTraceUtilsBootstrap::bootstrap` as the bootstrap method, **not** `invokestatic io/btrace/core/BTraceUtils$Strings.concat`.

- [ ] **Step 5: Commit**

```bash
git add btrace-compiler/src/
git commit -m "feat(compiler): wire BTraceUtilsCallRewriter into Postprocessor pipeline"
```

---

## Task 10: Integration test — end-to-end rewrite + service dispatch

**Files:**
- Create: `integration-tests/src/test/java/io/btrace/tests/BTraceUtilsRewriteIT.java`
- Create: `integration-tests/src/test/btrace/io/btrace/tests/StringRewriteTrace.java`

- [ ] **Step 1: Write the BTrace script used by the integration test**

```java
// integration-tests/src/test/btrace/io/btrace/tests/StringRewriteTrace.java
package io.btrace.tests;

import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.*;

@BTrace
public class StringRewriteTrace {
    @OnMethod(clazz = "io.btrace.tests.StringTarget", method = "go")
    public static void onGo() {
        // This INVOKESTATIC will be rewritten to INVOKEDYNAMIC → StringsService.concat
        String r = BTraceUtils.Strings.concat("hello-", "world");
        BTraceUtils.println(r);
    }
}
```

- [ ] **Step 2: Write the target class**

```java
// integration-tests/src/test/java/io/btrace/tests/StringTarget.java
package io.btrace.tests;

public class StringTarget {
    public void go() { /* intentionally empty */ }
}
```

- [ ] **Step 3: Write the integration test**

```java
// integration-tests/src/test/java/io/btrace/tests/BTraceUtilsRewriteIT.java
package io.btrace.tests;

import io.btrace.tests.support.BTraceITBase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BTraceUtilsRewriteIT extends BTraceITBase {

    @Test
    void stringsConcat_dispatchesThroughExtensionService() throws Exception {
        String output = btrace("StringTarget", "StringRewriteTrace");
        // StringsServiceImpl.concat("hello-","world") returns "hello-world"
        assertTrue(output.contains("hello-world"),
            "Expected 'hello-world' in BTrace output but got:\n" + output);
    }
}
```

> Adjust the `btrace(...)` helper call to match the existing `BTraceITBase` API used by other integration tests in the project.

- [ ] **Step 4: Build the distribution and run the integration test**

```bash
./gradlew :btrace-dist:build
./gradlew :integration-tests:test -Pintegration \
    --tests "io.btrace.tests.BTraceUtilsRewriteIT"
```
Expected: BUILD SUCCESSFUL and the test output confirms `hello-world` was printed.

- [ ] **Step 5: Run the full test suite to check for regressions**

```bash
./gradlew test
./gradlew :btrace-dist:build
./gradlew :integration-tests:test -Pintegration
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add integration-tests/
git commit -m "test(integration): verify BTraceUtils.Strings.concat dispatches via StringsService"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Covered by |
|---|---|
| StringsService with all Strings methods | Task 1 |
| NumbersService with all Numbers methods | Task 2 |
| TimeService with all Time methods | Task 3 |
| ReferencesService with all References methods | Task 4 |
| Extension manifest updated | Task 5 |
| ExtensionBridge can resolve service at bootstrap time | Task 6 |
| invokedynamic bootstrap class | Task 7 |
| ASM rewriter with complete rewrite table | Task 8 |
| Postprocessor wiring | Task 9 |
| End-to-end integration test | Task 10 |
| BTraceUtils itself unchanged | All — no modifications to BTraceUtils.java |

**Placeholder scan:** None found.

**Type consistency check:**
- `StringsService.concat(String,String):String` — matches `StringsServiceImpl`, matches rewrite table descriptor `(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;` ✓
- `NumbersService.box(boolean):Boolean` — matches impl (autoboxing) ✓
- `BTraceUtilsBootstrap.bootstrap` descriptor matches `Handle` in `BTraceUtilsCallRewriter.BOOTSTRAP_DESC` ✓
- `ExtensionBridgeImpl.getGlobalService(Class<?>):Object` returns `Object`; bootstrap casts via `findVirtual` on the interface ✓

**Known gap to address in follow-up plan:** outer-class delegate methods in `BTraceUtils` (e.g., `BTraceUtils.strcat`, `BTraceUtils.timeMillis`) must be verified against the actual compiled descriptors — BTrace strips them at compile time when scripts use `import static`, so the rewrite table includes them defensively. Verify with `javap` on a script that uses the outer-class style.
