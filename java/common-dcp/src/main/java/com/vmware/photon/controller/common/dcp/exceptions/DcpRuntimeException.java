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

package com.vmware.photon.controller.common.dcp.exceptions;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceErrorResponse;
import com.vmware.photon.controller.common.dcp.OperationLatch;

/**
 * This is to capture all DCP exceptions that we would normally not handle.
 * Since we do not expect this exception to be handled we make it a RuntimeException.
 */
public class DcpRuntimeException extends RuntimeException {

  private Operation requestedOperation;
  private OperationLatch.OperationResult operationResult;

  public DcpRuntimeException(DcpException cause) {
    super(cause);
    this.requestedOperation = cause.getRequestedOperation();
    this.operationResult = cause.getOperationResult();
  }

  public DcpRuntimeException(Operation operation) {
    super(operation.toString());
    this.requestedOperation = operation;
  }

  public DcpRuntimeException(Operation requestedOperation, OperationLatch.OperationResult operationResult) {
    super(operationResult.completedOperation.getBody(ServiceErrorResponse.class).message);
    this.requestedOperation = requestedOperation;
    this.operationResult = operationResult;
  }

  public DcpRuntimeException(Throwable cause) {
    super(cause);
  }

  public DcpRuntimeException(String message) {
    super(message);
  }

  public Operation getRequestedOperation() {
    return requestedOperation;
  }

  public OperationLatch.OperationResult getOperationResult() {
    return operationResult;
  }
}
