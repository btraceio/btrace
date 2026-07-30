/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.harness;

import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.AnnotatedElement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Dumps the threads of the test JVM and of every live target JVM when a test stops making progress.
 *
 * <p>An integration test that wedges used to produce nothing at all: no assertion failure, no stack
 * trace, and a job cancelled at its timeout twenty-five minutes later. Localising one meant reading
 * raw job logs and comparing timestamps, and the resulting hypotheses could not be confirmed
 * because no dump was ever captured. This closes that gap: the next stall names the blocked thread
 * on both sides of the connection.
 *
 * <p>It reports and does not intervene. Failing a stalled test is the job of the per-test timeout
 * in {@code junit-platform.properties}, which is set above this watchdog's deadline so that a dump
 * is always taken while the stall is still live.
 *
 * <p>Two frames are captured, thirty seconds apart. One frame cannot distinguish a wedged thread
 * from a slow one -- both look identical parked in a socket read -- so the difference between two
 * frames is the actual evidence.
 */
public class StallWatchdog
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  /** Absolute path, supplied by the build. See the {@code test} block in build.gradle. */
  private static final String DUMP_DIR_PROPERTY = "btrace.test.stallDumpDir";

  private static final String STALL_TIMEOUT_PROPERTY = "btrace.test.stallTimeoutMs";

  /**
   * The watchdog deadline defaults to six times the harness timeout. It cannot be much tighter:
   * {@code RuntimeTest} already allows a target four times that value for startup alone, so a
   * shorter deadline would fire on tests that are merely slow.
   */
  private static final long DEFAULT_TIMEOUT_MULTIPLIER = 6L;

  /**
   * A ceiling on the derived deadline. {@code btrace.test.timeoutMs} is an operator knob, and
   * raising it must not push the watchdog past the per-test timeout in {@code
   * junit-platform.properties} -- a dump taken after the test has been abandoned is no dump at all.
   */
  private static final long MAX_DERIVED_TIMEOUT_MS = 6L * 60L * 1000L;

  private static final long DEFAULT_GAP_MS = 30_000L;

  /** All targets together, so accumulated targets cannot stretch a capture without bound. */
  private static final long CAPTURE_BUDGET_MS = 60_000L;

  private static final long ECHO_TIMEOUT_MS = 10_000L;

  private static final ScheduledThreadPoolExecutor SCHEDULER =
      new ScheduledThreadPoolExecutor(
          2,
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
              Thread t = new Thread(runnable, "btrace-stall-watchdog");
              t.setDaemon(true);
              return t;
            }
          });

  static {
    // arm() runs three times per test and cancels the previous task each time; without this every
    // cancelled task would sit in the delayed queue until its deadline, which outlasts the suite.
    SCHEDULER.setRemoveOnCancelPolicy(true);
  }

  /**
   * The single armed test. Integration tests run sequentially in one worker JVM -- no {@code
   * forkEvery}, no parallel execution -- so one slot is correct and avoids threading state through
   * the extension store.
   */
  private static final AtomicReference<Armed> CURRENT = new AtomicReference<>();

  private static final AtomicBoolean STALE_DUMPS_CLEARED = new AtomicBoolean();

  /** Makes every dump path unique; two teardown stalls in one class share a label otherwise. */
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private static volatile Path dumpDirOverride;

  @Override
  public void beforeAll(ExtensionContext context) {
    arm(context, context.getDisplayName() + " (class setup)");
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    arm(context, context.getUniqueId());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Re-arm rather than simply disarming, so a stall in an @AfterEach method or between tests is
    // covered too. Deliberately resolved against the class context: a per-method @StallTimeout
    // belongs to the method, and carrying it into teardown would fire a dump for a test that has
    // already finished.
    ExtensionContext classContext = context.getParent().orElse(context);
    arm(classContext, classContext.getDisplayName() + " (teardown)");
  }

  @Override
  public void afterAll(ExtensionContext context) {
    disarm();
  }

  /** Points dumps at a caller-controlled directory. For the watchdog's own tests. */
  static void setDumpDirForTesting(Path dir) {
    dumpDirOverride = dir;
  }

  private void arm(ExtensionContext context, String label) {
    disarm();
    clearStaleDumpsOnce();
    long timeoutMs = resolveTimeoutMs(context);
    long gapMs = resolveGapMs(context);
    Armed armed = new Armed(label, gapMs);
    CURRENT.set(armed);
    armed.frameOne = SCHEDULER.schedule(armed::fireFirstFrame, timeoutMs, TimeUnit.MILLISECONDS);
  }

  private void disarm() {
    Armed previous = CURRENT.getAndSet(null);
    if (previous != null) {
      previous.disarm();
    }
    // Drops targets that have exited. Without this the registry only ever grows, and a stall late
    // in the run would spend its capture budget on jcmd calls against processes from earlier
    // classes instead of the target that actually matters.
    TargetRegistry.liveTargets();
  }

  private long resolveTimeoutMs(ExtensionContext context) {
    StallTimeout annotation = findAnnotation(context);
    if (annotation != null) {
      return annotation.millis();
    }
    Long explicit = Long.getLong(STALL_TIMEOUT_PROPERTY);
    if (explicit != null) {
      return explicit;
    }
    long derived = Long.getLong("btrace.test.timeoutMs", 60_000L) * DEFAULT_TIMEOUT_MULTIPLIER;
    return Math.min(derived, MAX_DERIVED_TIMEOUT_MS);
  }

  private long resolveGapMs(ExtensionContext context) {
    StallTimeout annotation = findAnnotation(context);
    return annotation != null ? annotation.gapMillis() : DEFAULT_GAP_MS;
  }

  private StallTimeout findAnnotation(ExtensionContext context) {
    ExtensionContext current = context;
    while (current != null) {
      AnnotatedElement element = current.getElement().orElse(null);
      if (element != null) {
        StallTimeout annotation = element.getAnnotation(StallTimeout.class);
        if (annotation != null) {
          return annotation;
        }
      }
      current = current.getParent().orElse(null);
    }
    return null;
  }

  /** Prints the captured report without letting a blocked stdout consume a scheduler thread. */
  private static void echoToStdout(final CharSequence report) {
    Thread printer =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                System.out.print(report);
                System.out.flush();
              }
            },
            "btrace-stall-echo");
    printer.setDaemon(true);
    printer.start();
    try {
      printer.join(ECHO_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Drops dumps left by an earlier run, once per JVM.
   *
   * <p>Done when the watchdog first arms rather than when it first writes: {@code cleanTest} does
   * not cover this directory, so otherwise a green run would upload a previous run's dumps as if
   * they were its own.
   */
  private static void clearStaleDumpsOnce() {
    if (!STALE_DUMPS_CLEARED.compareAndSet(false, true)) {
      return;
    }
    File[] stale = dumpDir().toFile().listFiles();
    if (stale == null) {
      return;
    }
    for (File file : stale) {
      if (!file.delete()) {
        file.deleteOnExit();
      }
    }
  }

  private static Path dumpDir() {
    Path override = dumpDirOverride;
    if (override != null) {
      return override;
    }
    String configured = System.getProperty(DUMP_DIR_PROPERTY);
    // The relative fallback exists only for a direct IDE run; the build always supplies an
    // absolute path, because a relative one would resolve against the test task's working
    // directory, which is the project directory rather than the repository root.
    return configured != null
        ? Paths.get(configured)
        : Paths.get("build", "reports", "stall-dumps");
  }

  /** One armed test, and the two capture frames it may produce. */
  private static final class Armed {
    private final String label;
    private final long gapMs;
    private final long startNanos = System.nanoTime();
    private final AtomicBoolean disarmed = new AtomicBoolean();
    private volatile ScheduledFuture<?> frameOne;
    private volatile ScheduledFuture<?> frameTwo;

    /**
     * Targets are snapshotted once, when the watchdog fires, and both frames dump those exact
     * processes. Re-reading the registry for the second frame could pick up a target belonging to
     * the next test, if this one unwedged during the gap.
     */
    private volatile List<TargetRegistry.Snapshot> targets = Collections.emptyList();

    Armed(String label, long gapMs) {
      this.label = label;
      this.gapMs = gapMs;
    }

    void disarm() {
      disarmed.set(true);
      cancel(frameOne);
      cancel(frameTwo);
    }

    private void cancel(ScheduledFuture<?> future) {
      if (future != null) {
        // Never interrupt: a capture already in flight should finish rather than leave a
        // half-written file.
        future.cancel(false);
      }
    }

    void fireFirstFrame() {
      if (disarmed.get()) {
        return;
      }
      targets = TargetRegistry.liveTargets();
      writeFrame(1);
      if (disarmed.get()) {
        return;
      }
      frameTwo = SCHEDULER.schedule(this::fireSecondFrame, gapMs, TimeUnit.MILLISECONDS);
    }

    void fireSecondFrame() {
      if (disarmed.get()) {
        return;
      }
      writeFrame(2);
      releaseTargets();
    }

    /**
     * Kills the targets this test was talking to, once both frames have been captured.
     *
     * <p>The watchdog would rather only observe, but observing alone does not end the stall. The
     * per-test timeout runs the test body on a thread JUnit does not mark as a daemon and abandons
     * it on timeout with nothing but an interrupt -- which a thread blocked in socket I/O ignores.
     * That surviving thread keeps the worker JVM alive, so the job burns its whole budget even
     * though the test has been marked failed. Closing the target's sockets is what actually
     * releases the blocked thread, and it also clears the orphaned JVMs that a stalled run used to
     * leave behind.
     *
     * <p>This happens only after two frames thirty seconds apart, so the evidence is already on
     * disk, and only to targets registered by the stalled test.
     */
    private void releaseTargets() {
      for (TargetRegistry.Snapshot target : targets) {
        try {
          if (target.getProcess().isAlive()) {
            target.getProcess().destroyForcibly();
            System.out.println(
                StallCapture.PREFIX + "released stalled target " + target.getLabel());
          }
        } catch (Throwable ignored) {
          // Nothing useful is left to do about a target we cannot kill.
        }
      }
    }

    private void writeFrame(int frame) {
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      StringBuilder echo = new StringBuilder();
      Path file = null;
      try {
        Path dir = dumpDir();
        Files.createDirectories(dir);
        file = dir.resolve(fileName(label, frame));
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
          // The file is the sink that matters: `if: always()` artifact upload survives a job
          // cancelled at its timeout, and the release path redirects all test output into a log it
          // only summarises once the command has exited.
          Appendable sink = new TeeAppendable(writer, echo);
          StallCapture.capture(sink, label, elapsedMs, frame, targets, CAPTURE_BUDGET_MS);
        }
      } catch (Throwable t) {
        StallCapture.line(echo, "stall dump could not be written to file: " + t);
        if (file != null) {
          StallCapture.line(echo, "intended path: " + file);
        }
      }
      // Second, and best-effort. This is written on a throwaway thread with a bounded join for the
      // reason the file is written first: System.out is a shared synchronised stream, and a torn-
      // down Gradle pipeline can block on it -- which would consume a scheduler thread and cost us
      // the second frame.
      echoToStdout(echo);
    }
  }

  /**
   * A file name derived from the test's unique id.
   *
   * <p>The unique id, not the method name: the test that motivated this watchdog is a
   * {@code @ParameterizedTest} whose four rows share one method name, and the row that stalls is
   * not the row that would win the race for the file. Truncated and hashed because a full
   * parameterised unique id exceeds the filename length limit.
   */
  static String fileName(String label, int frame) {
    return fileName(label, frame, SEQUENCE.incrementAndGet());
  }

  static String fileName(String label, int frame, int sequence) {
    StringBuilder sanitised = new StringBuilder(label.length());
    for (int i = 0; i < label.length(); i++) {
      char c = label.charAt(i);
      sanitised.append(
          (c >= 'A' && c <= 'Z')
                  || (c >= 'a' && c <= 'z')
                  || (c >= '0' && c <= '9')
                  || c == '.'
                  || c == '-'
              ? c
              : '_');
    }
    // The tail, not the head: a parameterised unique id is identical up to its invocation index,
    // so truncating from the front would give all four rows of a @ParameterizedTest one name.
    String trimmed =
        sanitised.length() > 120
            ? sanitised.substring(sanitised.length() - 120)
            : sanitised.toString();
    return sequence
        + "-"
        + trimmed
        + "-"
        + Integer.toHexString(label.hashCode())
        + "-frame"
        + frame
        + ".txt";
  }

  /** Writes to the file and accumulates a copy for stdout, flushing the file as sections land. */
  private static final class TeeAppendable implements Appendable, Flushable {
    private final Writer writer;
    private final StringBuilder echo;

    TeeAppendable(Writer writer, StringBuilder echo) {
      this.writer = writer;
      this.echo = echo;
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException {
      writer.append(csq);
      echo.append(csq);
      return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
      writer.append(csq, start, end);
      echo.append(csq, start, end);
      return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
      writer.append(c);
      echo.append(c);
      return this;
    }

    @Override
    public void flush() throws IOException {
      writer.flush();
    }
  }
}
