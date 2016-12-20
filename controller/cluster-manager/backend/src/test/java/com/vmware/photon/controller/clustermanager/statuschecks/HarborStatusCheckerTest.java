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

package com.vmware.photon.controller.clustermanager.statuschecks;

import com.vmware.photon.controller.clustermanager.clients.HarborClient;

import com.google.common.util.concurrent.FutureCallback;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.net.ConnectException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implements tests for {@link HarborStatusChecker}.
 */
public class HarborStatusCheckerTest {

  private static final int LATCH_AWAIT_TIMEOUT = 10;
  private static final String SERVER_ADDRESS = "10.146.22.40";

  private static HarborClient setupMockHarborClient(final boolean ready) throws Throwable {
    HarborClient harborClient = mock(HarborClient.class);
    doAnswer(invocation -> {
      ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(ready);
      return null;
    }).when(harborClient).checkStatus(anyString(), any(FutureCallback.class));

    return harborClient;
  }

  @Test
  public void testHarborIsReady() throws Throwable {
    HarborClient harborClient = setupMockHarborClient(true);
    HarborStatusChecker checker = new HarborStatusChecker(harborClient);

    final CountDownLatch latch = new CountDownLatch(1);
    checker.checkNodeStatus(SERVER_ADDRESS, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean isReady) {
        assertTrue(isReady);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(LATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testHarborIsNotReady() throws Throwable {
    HarborClient harborClient = setupMockHarborClient(false);
    HarborStatusChecker checker = new HarborStatusChecker(harborClient);

    final CountDownLatch latch = new CountDownLatch(1);
    checker.checkNodeStatus(SERVER_ADDRESS, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean isReady) {
        assertFalse(isReady);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(LATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testHarborConnectionFailure() throws Throwable {
    HarborClient harborClient = mock(HarborClient.class);
    doThrow(new ConnectException("Could not connect to Harbor"))
        .when(harborClient).checkStatus(anyString(), any(FutureCallback.class));

    HarborStatusChecker checker = new HarborStatusChecker(harborClient);

    final CountDownLatch latch = new CountDownLatch(1);
    checker.checkNodeStatus(SERVER_ADDRESS, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean isReady) {
        assertFalse(isReady);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(LATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }
}
