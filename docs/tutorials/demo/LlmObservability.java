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

import io.btrace.core.annotations.*;
import io.btrace.llm.LlmTraceService;
import io.btrace.contracts.ContractService;
import static io.btrace.core.BTraceUtils.*;

/**
 * Tutorial script for docs/tutorials/07-llm-observability.md.
 *
 * <p>Hooks the mocked OpenAI-client-shaped call in ../demo/LlmDemoApp.java ({@code
 * OpenAiClient.chat}) and feeds its token counts and latency into two bundled extensions at once:
 * {@code btrace-llm-trace}'s {@link LlmTraceService} for token/latency/cost accounting, and {@code
 * btrace-contracts}'s {@link ContractService} for a hard latency budget on the same call.
 *
 * <p>Statically imports {@code io.btrace.core.BTraceUtils.*} rather than the flat-DSL {@code
 * io.btrace.BTrace} facade: {@code field(...)} and {@code get(...)}/{@code getInt(...)} - needed
 * here to pull token counts off the mocked response object - live only on {@code BTraceUtils}.
 * BTrace's verifier only allows a script to call methods directly on itself, on {@code
 * BTraceUtils}/{@code BTrace}, on an injected {@code Extension} subtype, or on a type in an
 * injected service's own package - the demo's {@code ChatCompletion}/{@code Usage} classes match
 * none of those, so their fields are read reflectively instead of through a getter.
 */
@BTrace
public class LlmObservability {

  @Injected private static LlmTraceService llm;
  @Injected private static ContractService contracts;

  @OnMethod(clazz = "OpenAiClient", method = "chat", location = @Location(Kind.RETURN))
  public static void onChatReturn(@Return Object response, @Duration long durationNanos) {
    Object usage = get(field("ChatCompletion", "usage"), response);
    int promptTokens = 0;
    int completionTokens = 0;
    if (usage != null) {
      promptTokens = getInt(field("Usage", "promptTokens"), usage);
      completionTokens = getInt(field("Usage", "completionTokens"), usage);
    }

    llm.call("gpt-4o-mini")
        .provider("openai")
        .inputTokens(promptTokens)
        .outputTokens(completionTokens)
        .duration(durationNanos)
        .record();

    // 500ms latency budget on the same call - see Step 5 of the tutorial for why.
    contracts.checkLatency("chat/latency", durationNanos, 500_000_000L);
  }

  @OnTimer(10000)
  public static void report() {
    println("=== LLM + Contracts Dashboard ===");
    println(llm.getSummary());
    println(contracts.getSummary());
    println("==================================");
  }
}
