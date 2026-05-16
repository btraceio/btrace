/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.gpu;

/** ThreadLocal-pooled builder for {@link InferenceRecord}. Zero allocation. */
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
