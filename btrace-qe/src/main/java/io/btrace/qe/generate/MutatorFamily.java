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
package io.btrace.qe.generate;

import io.btrace.qe.model.CaseDef;
import java.util.List;
import java.util.Random;

/**
 * A source of generated resilience cases. A family must derive every choice from the supplied
 * {@link Random} so the same seed reproduces the same cases.
 */
public interface MutatorFamily {
  /** Stable family id, e.g. {@code agent-args.values}; part of every generated case id. */
  String id();

  /**
   * Generates exactly {@code budget} cases. Ids are {@code gen.<family>.<index>}; {@link
   * CaseGenerator} inserts the seed.
   */
  List<CaseDef> generate(Random rnd, int budget);
}
