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

package com.vmware.photon.controller.common.xenon;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class OperationLatch implements a 'latch' that lets us wait for operation completion synchronously.
 */
public class OperationLatch {
  public static final long DEFAULT_OPERATION_TIMEOUT_MICROS = TimeUnit.SECONDS.toMicros(60);

  private OperationResult operationResult;
  private CountDownLatch latch;
  private Operation operation;

  @VisibleForTesting
  protected Operation getCompletedOperation() {
    return operationResult.completedOperation;
  }

  public OperationLatch(Operation op) {
    operationResult = new OperationResult();
    this.latch = new CountDownLatch(1);
    this.operation = op;

    this.prepareOperation(op);
  }

  public Operation awaitOperationCompletion() throws InterruptedException, TimeoutException {
    this.awaitUsingOperationExpiration();
    return getCompletedOperation();
  }

  public Operation awaitOperationCompletion(long timeoutMicros) throws InterruptedException, TimeoutException {
    this.await(timeoutMicros, TimeUnit.MICROSECONDS);
    return getCompletedOperation();
  }

  @VisibleForTesting
  protected long calculateOperationTimeoutMicros() {
    if (this.operation.getExpirationMicrosUtc() > 0) {
      return this.operation.getExpirationMicrosUtc() - Utils.getNowMicrosUtc();
    }

    return DEFAULT_OPERATION_TIMEOUT_MICROS;
  }

  protected void awaitUsingOperationExpiration() throws InterruptedException, TimeoutException {
    this.await(calculateOperationTimeoutMicros(), TimeUnit.MICROSECONDS);
  }

  @VisibleForTesting
  protected void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    if (!latch.await(timeout, unit)) {
      String timeOutMessage = String.format(
          "TIMEOUT:{%s}, TimeUnit:{%s}, Operation:{%s}",
          timeout,
          unit,
          OperationUtils.createLogMessageWithStatus(this.operation));
      throw new TimeoutException(timeOutMessage);
    }
  }

  private void prepareOperation(Operation op) {
    op.setCompletion(new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        operationResult.completedOperation = completedOp;
        operationResult.operationFailure = failure;
        latch.countDown();
      }
    });
  }

  /**
   * This class allows us to capture all output from the DCP operation completion handler.
   */
  @VisibleForTesting
  protected static class OperationResult {
    public Operation completedOperation;
    public Throwable operationFailure;
  }
}
