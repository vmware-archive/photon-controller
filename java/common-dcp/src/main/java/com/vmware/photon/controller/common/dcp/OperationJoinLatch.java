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

package com.vmware.photon.controller.common.dcp;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class OperationJoinLatch implements a 'latch' that lets us wait for completion synchronously.
 */
public class OperationJoinLatch {

  public static final long DEFAULT_OPERATION_TIMEOUT_MICROS = TimeUnit.SECONDS.toMicros(60);

  private CountDownLatch latch;
  private OperationJoin join;

  public OperationJoinLatch(OperationJoin join) {
    this.latch = new CountDownLatch(1);
    this.join = join;

    this.prepareOperationJoin();
  }

  public void await() throws InterruptedException, TimeoutException {
    this.await(DEFAULT_OPERATION_TIMEOUT_MICROS, TimeUnit.MICROSECONDS);
  }

  public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    if (!latch.await(timeout, unit)) {
      throw new TimeoutException(String.format("Timeout:{%s}, TimeUnit:{%s}", timeout, unit));
    }
  }

  private void prepareOperationJoin() {
    this.join.setCompletion(
        (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
          latch.countDown();
        });
  }
}
