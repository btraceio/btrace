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
package resources;

/** Calls whose locals, added by instrumentation, live on only some of the paths to a join point. */
public class CallInBranchTarget {
  public void callInTryInsideIf(boolean deep) {
    if (deep) {
      try {
        work();
      } catch (IllegalStateException e) {
        return;
      }
    }
    done();
  }

  public void callsInIfAndElse(boolean flag) {
    if (flag) {
      work();
    } else {
      done();
    }
  }

  public int callsInConditionalExpression(boolean flag) {
    return flag ? value() : value() + 1;
  }

  public void callsInSwitch(int kind) {
    switch (kind) {
      case 1:
        work();
        break;
      case 2:
        done();
        break;
      default:
        break;
    }
    done();
  }

  public void callInLoop(int count) {
    for (int i = 0; i < count; i++) {
      if (i % 2 == 0) {
        work();
      }
    }
  }

  private void work() {}

  private void done() {}

  private int value() {
    return 1;
  }
}
