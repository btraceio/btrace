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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Target application for demos/05-rag-and-inference: a mocked retrieval-augmented question
 * answerer. Run with {@code java RagSearchApp.java} (JDK 11+, no dependencies).
 *
 * <p>The defect: roughly 1 query in 10 makes {@code VectorStore.search} return an empty result.
 * The app carries on and "generates" an answer from no context, so nobody notices.
 */
public class RagSearchApp {
  public static void main(String[] args) throws Exception {
    VectorStore store = new VectorStore();
    OnnxSession onnx = new OnnxSession();
    AtomicLong answered = new AtomicLong();
    Random rnd = new Random();

    Thread worker =
        new Thread(
            () -> {
              try {
                while (true) {
                  String question = "question-" + rnd.nextInt(10_000);
                  float[] embedding = onnx.run(new float[384]);
                  store.search(question, 5);
                  Thread.sleep(100 + rnd.nextInt(100)); // "generate" the answer
                  answered.incrementAndGet();
                  Thread.sleep(50);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            "rag-worker");
    worker.setDaemon(true);
    worker.start();

    System.out.println("[demo] rag answerer running - stop with Ctrl+C");
    while (true) {
      Thread.sleep(5_000);
      System.out.printf("[demo] answered %d questions%n", answered.get());
    }
  }
}

class Chunk {
  int tokens;
  float score;

  Chunk(int tokens, float score) {
    this.tokens = tokens;
    this.score = score;
  }
}

class SearchResult {
  List<Chunk> chunks;
  int count;
  float topScore;
  int totalTokens;

  SearchResult(List<Chunk> chunks) {
    this.chunks = chunks;
    this.count = chunks.size();
    for (Chunk c : chunks) {
      this.topScore = Math.max(this.topScore, c.score);
      this.totalTokens += c.tokens;
    }
  }
}

class VectorStore {
  private final Random rnd = new Random();

  /** Takes 20-80 ms; about 1 call in 10 finds nothing - find it with BTrace. */
  SearchResult search(String query, int topK) throws InterruptedException {
    Thread.sleep(20 + rnd.nextInt(60));
    List<Chunk> chunks = new ArrayList<>();
    if (rnd.nextInt(10) != 0) {
      for (int i = 0; i < topK; i++) {
        chunks.add(new Chunk(80 + rnd.nextInt(220), 0.9f - i * 0.07f - rnd.nextFloat() * 0.05f));
      }
    }
    return new SearchResult(chunks);
  }
}

class OnnxSession {
  private boolean warm;
  private final Random rnd = new Random();

  /** Mocked local embedding model: 5-15 ms per run, ~300 ms warm-up on the first call. */
  float[] run(float[] input) throws InterruptedException {
    if (!warm) {
      warm = true;
      Thread.sleep(300);
    }
    Thread.sleep(5 + rnd.nextInt(10));
    return new float[input.length];
  }
}
