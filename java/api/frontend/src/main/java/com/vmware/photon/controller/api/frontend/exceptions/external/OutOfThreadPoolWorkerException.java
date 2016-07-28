/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.api.frontend.exceptions.external;

/**
 * Gets thrown when running out of ThreadPool worker.
 */
public class OutOfThreadPoolWorkerException extends ExternalException {

  public OutOfThreadPoolWorkerException() {
    super(ErrorCode.OUT_OF_THREAD_POOL_WORKER);
  }

  @Override
  public String getMessage() {
    return "Unable to process request because system is currently busy.";
  }
}
