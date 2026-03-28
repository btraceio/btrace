package org.openjdk.btrace.llm;

/**
 * Mutable builder implementing the {@link CallRecord} fluent API.
 *
 * <p>Instances are pooled per-thread via {@link ThreadLocal} to avoid
 * heap allocation on every {@code call()} invocation. This makes the
 * builder safe for hot-path instrumentation — no garbage is produced.
 *
 * <p>Not thread-safe — intended to be created, configured, and recorded
 * within a single BTrace handler method invocation on the same thread.
 */
final class CallRecordImpl implements CallRecord {

  String model;
  String providerVal;
  int inputTok;
  int outputTok;
  int cacheReadTok;
  int cacheCreateTok;
  boolean isStreaming;
  long ttftVal;
  long durationVal;

  private LlmTraceServiceImpl service;

  CallRecordImpl() {
    // Created once per thread via ThreadLocal
  }

  /** Resets all fields and binds this record to a new call. */
  CallRecordImpl reset(LlmTraceServiceImpl service, String model) {
    this.service = service;
    this.model = model;
    this.providerVal = null;
    this.inputTok = 0;
    this.outputTok = 0;
    this.cacheReadTok = 0;
    this.cacheCreateTok = 0;
    this.isStreaming = false;
    this.ttftVal = 0;
    this.durationVal = 0;
    return this;
  }

  @Override
  public CallRecord provider(String provider) {
    this.providerVal = provider;
    return this;
  }

  @Override
  public CallRecord inputTokens(int tokens) {
    this.inputTok = tokens;
    return this;
  }

  @Override
  public CallRecord outputTokens(int tokens) {
    this.outputTok = tokens;
    return this;
  }

  @Override
  public CallRecord cacheReadTokens(int tokens) {
    this.cacheReadTok = tokens;
    return this;
  }

  @Override
  public CallRecord cacheCreationTokens(int tokens) {
    this.cacheCreateTok = tokens;
    return this;
  }

  @Override
  public CallRecord streaming() {
    this.isStreaming = true;
    return this;
  }

  @Override
  public CallRecord timeToFirstToken(long nanos) {
    this.ttftVal = nanos;
    return this;
  }

  @Override
  public CallRecord duration(long nanos) {
    this.durationVal = nanos;
    return this;
  }

  @Override
  public void record() {
    service.commitCallRecord(this);
  }
}
