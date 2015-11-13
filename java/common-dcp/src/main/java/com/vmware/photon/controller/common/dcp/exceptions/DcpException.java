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
import com.vmware.photon.controller.common.dcp.OperationLatch;

/**
 * This is to capture all DCP exceptions that we would normally want the clients to handle.
 */
public class DcpException extends Throwable {

  private Operation requestedOperation;
  private OperationLatch.OperationResult operationResult;

  public DcpException(Operation operation) {
    super(operation.toString());

    this.requestedOperation = operation;
  }

  public DcpException(Operation requestedOperation, OperationLatch.OperationResult operationResult) {
      super(operationResult.completedOperation == null ?
              requestedOperation.toString() : operationResult.completedOperation.toString(),
          operationResult
          .operationFailure);

    this.requestedOperation = requestedOperation;
    this.operationResult = operationResult;
  }

  public Operation getRequestedOperation() {
    return requestedOperation;
  }

  public OperationLatch.OperationResult getOperationResult() {
    return operationResult;
  }
}
