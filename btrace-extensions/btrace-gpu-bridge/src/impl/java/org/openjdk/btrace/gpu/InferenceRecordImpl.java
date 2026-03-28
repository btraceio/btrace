package org.openjdk.btrace.gpu;

/**
 * ThreadLocal-pooled builder for {@link InferenceRecord}. Zero allocation.
 */
final class InferenceRecordImpl implements InferenceRecord {

  String runtime;
  String modelName;
  int batchSizeVal;
  long inputElem;
  long outputElem;
  String deviceTypeVal;
  int deviceIdVal;
  long durationVal;

  private GpuBridgeServiceImpl service;

  InferenceRecordImpl() {}

  InferenceRecordImpl reset(GpuBridgeServiceImpl service, String runtime, String modelName) {
    this.service = service;
    this.runtime = runtime;
    this.modelName = modelName;
    this.batchSizeVal = 1;
    this.inputElem = 0;
    this.outputElem = 0;
    this.deviceTypeVal = null;
    this.deviceIdVal = 0;
    this.durationVal = 0;
    return this;
  }

  @Override
  public InferenceRecord batchSize(int size) {
    this.batchSizeVal = size;
    return this;
  }

  @Override
  public InferenceRecord inputElements(long elements) {
    this.inputElem = elements;
    return this;
  }

  @Override
  public InferenceRecord outputElements(long elements) {
    this.outputElem = elements;
    return this;
  }

  @Override
  public InferenceRecord deviceType(String type) {
    this.deviceTypeVal = type;
    return this;
  }

  @Override
  public InferenceRecord deviceId(int id) {
    this.deviceIdVal = id;
    return this;
  }

  @Override
  public InferenceRecord duration(long nanos) {
    this.durationVal = nanos;
    return this;
  }

  @Override
  public void record() {
    service.commitInferenceRecord(this);
  }
}
