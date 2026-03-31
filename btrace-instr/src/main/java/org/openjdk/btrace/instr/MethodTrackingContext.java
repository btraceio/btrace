/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.runtime.Interval;

/**
 * Context for emitting method tracking bytecode (sampling and timing). Replaces the template
 * system with direct bytecode generation.
 */
public class MethodTrackingContext {
  private static final String METHOD_COUNTER_CLASS = "org/openjdk/btrace/instr/MethodTracker";

  private final Assembler asm;
  private final MethodInstrumentorHelper helper;
  private final String probeClassName;
  private final int baseMethodId;
  private final org.objectweb.asm.MethodVisitor mv;

  // Configuration (set via entry)
  private boolean timed = false;
  private boolean sampled = false;
  private Sampled.Sampler samplerKind = Sampled.Sampler.None;
  private int samplerMean = -1;
  private final Collection<Interval> levelIntervals = new ArrayList<>();

  // State (per entry point)
  private int entryTsVar = Integer.MIN_VALUE;
  private int sHitVar = Integer.MIN_VALUE;
  private int durationVar = Integer.MIN_VALUE;
  private boolean durationComputed = false;
  private boolean prologueEmitted = false;
  private Label elseLabel = null;
  private Label samplerLabel = null;

  public MethodTrackingContext(
      org.objectweb.asm.MethodVisitor mv,
      Assembler asm,
      MethodInstrumentorHelper helper,
      String probeClassName,
      int baseMethodId) {
    this.mv = mv;
    this.asm = asm;
    this.helper = helper;
    this.probeClassName = probeClassName;
    this.baseMethodId = baseMethodId;
  }

  /**
   * Emit entry bytecode for sampling/timing setup.
   *
   * @param timed whether to enable timing
   * @param samplerKind sampler type (None, Const, Adaptive)
   * @param samplerMean mean hits between samples
   * @param methodId method ID for tracking
   * @param levelStr level match condition
   */
  public void emitEntry(
      boolean timed, Sampled.Sampler samplerKind, int samplerMean, int methodId, String levelStr) {
    // Idempotence: ensure prologue emits only once for shared contexts
    if (prologueEmitted) {
      return;
    }

    this.timed = timed;
    this.samplerKind = samplerKind;
    this.samplerMean = samplerMean;
    this.sampled = samplerKind != Sampled.Sampler.None && samplerMean > 0;

    if (levelStr != null && !levelStr.isEmpty()) {
      Interval itv = Interval.fromString(levelStr);
      levelIntervals.add(itv);
    }

    if (sampled) {
      MethodTracker.registerCounter(methodId, samplerMean);
      if (timed) {
        emitTimingSamplerEntry(methodId);
      } else {
        emitSamplerEntry(methodId);
      }
    } else {
      if (timed) {
        emitTimingEntry();
      }
    }

    prologueEmitted = true;
  }

  /**
   * Emit test sample bytecode - generates branch logic for sampling condition.
   *
   * @param collectTime whether to collect timing information
   * @param methodId method ID for tracking
   */
  public void emitTestSample(boolean collectTime, int methodId) {
    samplerLabel = new Label();
    boolean expanded = false;

    if (sampled) {
      emitSamplerTest();
      if (timed && collectTime) {
        emitTimingSamplerTest(methodId);
      }
      expanded = true;
    } else {
      if (timed && collectTime) {
        emitTimingTest();
        expanded = true;
      }
    }

    if (expanded) {
      asm.label(samplerLabel);
      helper.insertFrameSameStack(samplerLabel);
    }
    samplerLabel = null;
  }

  /** Emit else label bytecode - jump target for non-sampled executions. */
  public void emitElse() {
    if (elseLabel != null) {
      asm.label(elseLabel);
      helper.insertFrameSameStack(elseLabel);
      elseLabel = null;
    }
  }

  /**
   * Emit duration bytecode - pushes method execution duration onto stack.
   *
   * @return true if duration was computed, false if zero pushed
   */
  public boolean emitDuration() {
    if (!durationComputed) {
      asm.ldc(0L);
      return false;
    } else {
      asm.loadLocal(Type.LONG_TYPE, durationVar);
      return true;
    }
  }

  /**
   * Emit exit bytecode - update adaptive sampling statistics.
   *
   * @param methodId method ID for tracking
   */
  public void emitExit(int methodId) {
    if (samplerKind == Sampled.Sampler.Adaptive) {
      Label l = new Label();
      asm.loadLocal(Type.INT_TYPE, sHitVar)
          .jump(IFEQ, l)
          .ldc(methodId)
          .invokeRuntime(METHOD_COUNTER_CLASS, "updateEndTs", "(I)V")
          .label(l);
      helper.insertFrameSameStack(l);
    }
  }

  /** Reset state for multiple return points. */
  public void reset() {
    entryTsVar = Integer.MIN_VALUE;
    sHitVar = Integer.MIN_VALUE;
    durationComputed = false;
  }

  /** Reset only duration computation flag (for multiple error handlers). */
  public void resetDuration() {
    durationComputed = false;
  }

  /**
   * Compute duration for error handlers - no level checks or sampling logic.
   * Just computes nanoTime() - entryTs and stores to durationVar.
   */
  public void computeDurationForErrorHandler() {
    if (!durationComputed) {
      if (entryTsVar != Integer.MIN_VALUE) {
        asm.invokeStatic("java/lang/System", "nanoTime", "()J")
            .loadLocal(Type.LONG_TYPE, entryTsVar)
            .sub(Type.LONG_TYPE);
      } else {
        asm.ldc(0L);
      }
      if (durationVar == Integer.MIN_VALUE) {
        durationVar = helper.storeAsNew();
      } else {
        asm.storeLocal(Type.LONG_TYPE, durationVar);
      }
      durationComputed = true;
    }
  }

  /**
   * Emit level check for a handler. Uses cached level variable to avoid multiple loads.
   * Can be called multiple times for different handlers - first call loads and caches the level,
   * subsequent calls reuse the cached value.
   *
   * @param levelStr level match condition (e.g., {@code ">=1"})
   * @return Label to jump to if level check fails, or null if no check needed
   */
  public Label emitHandlerLevelCheck(String levelStr) {
    if (levelStr == null || levelStr.isEmpty()) {
      return null;
    }

    Interval itv = Interval.fromString(levelStr);

    // Check if level check is actually needed
    if (itv.getA() <= 0 && itv.getB() >= Integer.MAX_VALUE) {
      return null; // Always passes
    }

    Label skipLabel = new Label();

    int lo = itv.getA() <= 0 ? 0 : itv.getA();
    int hi = itv.getB();
    // INDY returns 1 if probe level in [lo, hi], 0 otherwise; skip handler if out of range
    asm.invokeDynamic("levelCheck", "()I", Assembler.LEVEL_BOOTSTRAP_HANDLE, probeClassName, lo, hi)
        .jump(IFEQ, skipLabel);

    return skipLabel;
  }

  // Private helper methods for bytecode generation

  private void emitTimingSamplerEntry(int mid) {
    if (durationVar == Integer.MIN_VALUE) {
      asm.ldc(0L);
      durationVar = helper.storeAsNew();
    }

    if (sHitVar == Integer.MIN_VALUE && entryTsVar == Integer.MIN_VALUE) {
      Label skipTarget =
          addLevelChecks(
              () -> {
                if (entryTsVar == Integer.MIN_VALUE) {
                  asm.ldc(0L);
                  entryTsVar = helper.storeAsNew();
                }
                if (sHitVar == Integer.MIN_VALUE) {
                  asm.ldc(0);
                  sHitVar = helper.storeAsNew();
                }
              });

      asm.ldc(mid);
      switch (samplerKind) {
        case Const:
          asm.invokeRuntime(METHOD_COUNTER_CLASS, "hitTimed", "(I)J");
          break;
        case Adaptive:
          asm.invokeRuntime(METHOD_COUNTER_CLASS, "hitTimedAdaptive", "(I)J");
          break;
        default:
          // do nothing
      }

      asm.dup2();
      if (entryTsVar == Integer.MIN_VALUE) {
        entryTsVar = helper.storeAsNew();
      } else {
        asm.storeLocal(Type.LONG_TYPE, entryTsVar);
      }
      mv.visitInsn(L2I);
      if (sHitVar == Integer.MIN_VALUE) {
        sHitVar = helper.storeAsNew();
      } else {
        asm.storeLocal(Type.INT_TYPE, sHitVar);
      }

      if (skipTarget != null) {
        asm.label(skipTarget);
        helper.insertFrameSameStack(skipTarget);
      }
    }
  }

  private void emitSamplerEntry(int mid) {
    if (sHitVar == Integer.MIN_VALUE) {
      Label skipTarget =
          addLevelChecks(
              () -> {
                if (sHitVar == Integer.MIN_VALUE) {
                  asm.ldc(0);
                  sHitVar = helper.storeAsNew();
                }
              });

      asm.ldc(mid);
      switch (samplerKind) {
        case Const:
          asm.invokeRuntime(METHOD_COUNTER_CLASS, "hit", "(I)Z");
          break;
        case Adaptive:
          asm.invokeRuntime(METHOD_COUNTER_CLASS, "hitAdaptive", "(I)Z");
          break;
        default:
          // do nothing
      }

      if (sHitVar == Integer.MIN_VALUE) {
        sHitVar = helper.storeAsNew();
      } else {
        asm.storeLocal(Type.INT_TYPE, sHitVar);
      }

      if (skipTarget != null) {
        asm.label(skipTarget);
        helper.insertFrameSameStack(skipTarget);
      }
    }
  }

  private void emitTimingEntry() {
    if (entryTsVar == Integer.MIN_VALUE) {
      if (durationVar == Integer.MIN_VALUE) {
        asm.ldc(0L);
        durationVar = helper.storeAsNew();
      }

      Label skipTarget =
          addLevelChecks(
              () -> {
                asm.ldc(0L);
                entryTsVar = helper.storeAsNew();
              });

      asm.invokeStatic("java/lang/System", "nanoTime", "()J");
      if (entryTsVar == Integer.MIN_VALUE) {
        entryTsVar = helper.storeAsNew();
      } else {
        asm.storeLocal(Type.LONG_TYPE, entryTsVar);
      }

      if (skipTarget != null) {
        asm.label(skipTarget);
        helper.insertFrameSameStack(skipTarget);
      }
    }
  }

  private void emitSamplerTest() {
    if (sHitVar != Integer.MIN_VALUE) {
      elseLabel = new Label();
      addLevelChecks(samplerLabel);
      asm.loadLocal(Type.INT_TYPE, sHitVar).jump(IFEQ, elseLabel);
    }
  }

  private void emitTimingSamplerTest(int mid) {
    if (!durationComputed) {
      if (entryTsVar != Integer.MIN_VALUE) {
        asm.ldc(mid)
            .invokeRuntime(METHOD_COUNTER_CLASS, "getEndTs", "(I)J")
            .loadLocal(Type.LONG_TYPE, entryTsVar)
            .sub(Type.LONG_TYPE);
      } else {
        asm.ldc(0L);
      }
      asm.storeLocal(Type.LONG_TYPE, durationVar);
      durationComputed = true;
    }
  }

  private void emitTimingTest() {
    if (!durationComputed) {
      addLevelChecks(samplerLabel);
      if (entryTsVar != Integer.MIN_VALUE) {
        asm.invokeStatic("java/lang/System", "nanoTime", "()J")
            .loadLocal(Type.LONG_TYPE, entryTsVar)
            .sub(Type.LONG_TYPE);
      } else {
        asm.ldc(0L);
      }
      asm.storeLocal(Type.LONG_TYPE, durationVar);
      durationComputed = true;
    }
  }

  private Label addLevelChecks(Label skip) {
    return addLevelChecks(skip, null);
  }

  private Label addLevelChecks(Runnable initializer) {
    return addLevelChecks(null, initializer);
  }

  private Label addLevelChecks(Label skip, Runnable initializer) {
    Label skipTarget = null;
    if (!levelIntervals.isEmpty()) {
      List<Interval> optimized = Interval.invert(levelIntervals);
      boolean generateBranch = true;

      if (optimized.size() == 1) {
        Interval i = optimized.get(0);
        if (i.isNone() || (i.getA() == Integer.MIN_VALUE && i.getB() == -1)) {
          generateBranch = false;
        }
      }

      if (generateBranch) {
        if (initializer != null) {
          initializer.run();
        }

        skipTarget = skip != null ? skip : new Label();

        for (Interval i : optimized) {
          int lo = i.getA() == Integer.MIN_VALUE ? Integer.MIN_VALUE : i.getA();
          int hi = i.getB();
          // INDY returns 1 if level in [lo, hi]; jump to skipTarget if in skip range
          asm.invokeDynamic(
                  "levelCheck", "()I", Assembler.LEVEL_BOOTSTRAP_HANDLE, probeClassName, lo, hi)
              .jump(IFNE, skipTarget);
        }
      }
    }
    return skipTarget;
  }
}
