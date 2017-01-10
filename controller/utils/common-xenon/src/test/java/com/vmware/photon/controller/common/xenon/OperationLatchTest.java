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

import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link OperationLatch}.
 */
public class OperationLatchTest {

  private OperationLatch latch;
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
  public class AwaitWithoutExpirationTests {
    @BeforeMethod
    public void setUp() {
      operation = new Operation();
      latch = spy(new OperationLatch(operation));
    }

    @Test
    public void testWithDefaultTimeout() throws Throwable {
      operation.complete();
      Operation result = latch.awaitOperationCompletion();

      assertThat(result, is(operation));
      verify(latch, times(1)).await(OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS, TimeUnit.MICROSECONDS);
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void testTimeout() throws Throwable {
      latch = spy(new OperationLatch(operation));
      when(latch.calculateOperationTimeoutMicros()).thenReturn(TimeUnit.MILLISECONDS.toMicros(1));
      latch.awaitOperationCompletion();
    }

    @Test
    public void testOperationFailure() throws Throwable {
      operation.fail(new IllegalArgumentException());
      Operation result = latch.awaitOperationCompletion();
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_BAD_REQUEST));
    }
  }

  /**
   * Tests for the await method and its overloads when expiration is set on the operation.
   */
  public class AwaitWithExpirationTests {

    @BeforeMethod
    public void setUp() {
      operation = new Operation();
      long expirationMicros = Utils.getNowMicrosUtc() + TimeUnit.SECONDS.toMicros(30);
      operation.setExpiration(expirationMicros);
      latch = new OperationLatch(operation);
    }

    @Test
    public void testWithExpiration() throws Throwable {
      latch = spy(new OperationLatch(operation));

      operation.complete();
      latch.awaitOperationCompletion();

      //range check the timeout since the latch did not throw an exception
      //and we cannot be sure what exact timeout would get used
      long timeOutRemaining = operation.getExpirationMicrosUtc() - Utils.getNowMicrosUtc();

      ArgumentCaptor<Long> timeoutArgument = ArgumentCaptor.forClass(Long.class);
      ArgumentCaptor<TimeUnit> timeUnitArgument = ArgumentCaptor.forClass(TimeUnit.class);

      verify(latch, times(1)).await(timeoutArgument.capture(), timeUnitArgument.capture());

      assertThat(timeoutArgument.getValue(), is(lessThan(TimeUnit.SECONDS.toMicros(60))));
      assertThat(timeoutArgument.getValue(), is(greaterThan(timeOutRemaining)));
      assertThat(timeUnitArgument.getValue(), is(TimeUnit.MICROSECONDS));
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void testDefaultTimeout() throws Throwable {
      operation.setExpiration(TimeUnit.MILLISECONDS.toMicros(1));
      latch.awaitOperationCompletion();
    }

    @Test
    public void testLatchTimeout() throws Throwable {
      try {
        latch.awaitOperationCompletion(1);
        Assert.fail("Operation Latch should have timed out");
      } catch (TimeoutException e) {
        assertThat(e.getMessage(),
            containsString(String.format("TIMEOUT:{%s}, TimeUnit:{%s}, Operation:{%s}",
                1, TimeUnit.MICROSECONDS, OperationUtils.createLogMessageWithStatus(operation))));
      }
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void testOperationTimeout() throws Throwable {
      operation.setExpiration(TimeUnit.MILLISECONDS.toMicros(1));
      latch.awaitOperationCompletion(30);
    }

    @Test
    public void testOperationFailure() throws Throwable {
      operation.fail(new IllegalArgumentException());
      Operation result = latch.awaitOperationCompletion();
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_BAD_REQUEST));
    }
  }
}
