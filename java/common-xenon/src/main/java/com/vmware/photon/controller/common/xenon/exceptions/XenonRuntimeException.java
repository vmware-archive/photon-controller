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

package com.vmware.photon.controller.common.xenon.exceptions;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;

/**
 * This is to capture all Xenon exceptions that we would normally not handle. Since we do not expect this exception to
 * be handled we make it a RuntimeException.
 */
public class XenonRuntimeException extends RuntimeException {

  private Operation requestedOperation;
  private Operation completedOperation;

  public XenonRuntimeException(XenonException cause) {
    super(cause);
    this.requestedOperation = cause.getRequestedOperation();
    this.completedOperation = cause.getCompletedOperation();
  }

  public XenonRuntimeException(Operation requestedOperation, Operation completedOperation) {
    super(completedOperation.getBody(ServiceErrorResponse.class).message);
    this.requestedOperation = requestedOperation;
    this.completedOperation = completedOperation;
  }

  public XenonRuntimeException(Throwable cause) {
    super(cause);
  }

  public XenonRuntimeException(String message) {
    super(message);
  }

  public Operation getRequestedOperation() {
    return requestedOperation;
  }

  public Operation getCompletedOperation() {
    return completedOperation;
  }
}
