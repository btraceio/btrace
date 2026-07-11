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

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo application for docs/tutorials/07-llm-observability.md.
 *
 * <p>Simulates a tiny AI-assisted ticket summarizer that calls a mocked OpenAI-client-shaped chat
 * completion API ({@link OpenAiClient#chat(String)}) for every ticket. {@code OpenAiClient} never
 * leaves the JVM - it's a stand-in for a real client SDK, shaped like one only so a BTrace probe
 * has something realistic to instrument: a single {@code chat(prompt)} call that returns a
 * response carrying per-call token usage.
 *
 * <p>One deliberate defect is hidden inside:
 *
 * <ul>
 *   <li>roughly 1 call in 12 (~8%) "runs away" - the mocked model keeps generating well past its
 *       normal reply length, which shows up as both a token/cost spike and a blown latency
 *       budget.
 * </ul>
 *
 * <p>Run it with a single command (JDK 11+): {@code java LlmDemoApp.java}
 *
 * <p>No dependencies, no build, no real network calls. It keeps "answering tickets" until you
 * stop it with Ctrl+C.
 */
public class LlmDemoApp {
  public static void main(String[] args) throws Exception {
    OpenAiClient client = new OpenAiClient();
    AtomicLong answered = new AtomicLong();

    Thread worker =
        new Thread(
            () -> {
              Random rnd = new Random();
              while (true) {
                try {
                  client.chat("Summarize ticket #" + rnd.nextInt(100_000));
                  answered.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            },
            "llm-worker");
    worker.setDaemon(true);
    worker.start();

    System.out.println("[demo] llm ticket summarizer running - stop with Ctrl+C");
    while (true) {
      Thread.sleep(5_000);
      System.out.printf("[demo] answered %d tickets%n", answered.get());
    }
  }
}

/**
 * Mock of an OpenAI-client-shaped chat completion call: a single {@code chat(prompt)} method
 * returning a response object that carries per-call token usage - the method shape most
 * OpenAI-style Java client SDKs expose. Not public on purpose: its binary name is just
 * "OpenAiClient", same convention as {@code OrderService} in ../demo/DemoApp.java.
 */
class OpenAiClient {
  private final Random rnd = new Random();

  ChatCompletion chat(String prompt) throws InterruptedException {
    int promptTokens = 60 + rnd.nextInt(120);
    int completionTokens = 80 + rnd.nextInt(120);
    long latencyMs = 150 + rnd.nextInt(250);

    // Roughly 1 call in 12: the model "runs away" - far more output tokens and much slower.
    if (rnd.nextInt(100) < 8) {
      completionTokens *= 14;
      latencyMs += 2500 + rnd.nextInt(1500);
    }
    Thread.sleep(latencyMs);
    return new ChatCompletion("gpt-4o-mini", new Usage(promptTokens, completionTokens));
  }
}

/** Mock chat-completion response. Field names mirror the shape LLM client SDKs commonly use. */
class ChatCompletion {
  final String model;
  final Usage usage;

  ChatCompletion(String model, Usage usage) {
    this.model = model;
    this.usage = usage;
  }
}

/** Mock token-usage block - the same two counters every major LLM chat API reports. */
class Usage {
  final int promptTokens;
  final int completionTokens;

  Usage(int promptTokens, int completionTokens) {
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
  }
}
