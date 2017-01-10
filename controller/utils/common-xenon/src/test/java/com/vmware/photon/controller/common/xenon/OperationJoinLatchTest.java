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
import com.vmware.xenon.common.OperationJoin;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link OperationJoinLatch}.
 */
public class OperationJoinLatchTest {
  private OperationJoinLatch latch;

  private OperationJoin join;
  private Operation operation;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the await method and its overloads.
   */
  public class AwaitTests {
    @BeforeMethod
    public void setUp() {
      operation = new Operation();
      join = OperationJoin.create(operation);
      latch = spy(new OperationJoinLatch(join));
    }

    @Test
    public void testWithDefaultTimeout() throws Throwable {
      operation.complete();
      latch.await();

      verify(latch, times(1)).await(OperationJoinLatch.DEFAULT_OPERATION_TIMEOUT_MICROS, TimeUnit.MICROSECONDS);
    }

    @Test
    public void testWithCustomTimeout() throws Throwable {
      operation.complete();
      latch.await(5, TimeUnit.MICROSECONDS);

      verify(latch, times(1)).await(5, TimeUnit.MICROSECONDS);
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void testTimeout() throws Throwable {
      latch.await(5, TimeUnit.MICROSECONDS);
    }
  }
}
