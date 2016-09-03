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

package com.vmware.photon.controller.clustermanager.clients;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link HarborClient}.
 */
public class HarborClientTest {
  private static final int LATCH_AWAIT_TIMEOUT = 10;
  private CloseableHttpAsyncClient asyncHttpClient;

  @Test
  private void dummy() {
  }

  /**
   * Implements tests for the checkStatus method.
   */
  public class CheckStatusTest {
    private static final String CONNECTION_STRING = "http://10.146.22.40";

    @Test
    public void testCheckStatusSuccess() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocks("", HttpStatus.SC_OK);
      HarborClient client = new HarborClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.checkStatus(CONNECTION_STRING, new FutureCallback<Boolean>() {
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
    public void testCheckStatusFailure() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocksToThrowInCallback(new SocketException("testing"));
      HarborClient client = new HarborClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.checkStatus(CONNECTION_STRING, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(@Nullable Boolean isReady) {
          fail("getNodes was expected to fail.");
          latch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          assertTrue(t instanceof SocketException);
          latch.countDown();
        }
      });

      assertThat(latch.await(LATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
    }
  }
}
