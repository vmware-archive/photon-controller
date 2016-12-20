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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MesosClient}.
 */
public class MesosClientTest {
  private CloseableHttpAsyncClient asyncHttpClient;

  @Test
  private void dummy() {
  }

  /**
   * Implements tests for the getMasterLeader method.
   */
  public class GetMasterLeaderTest {
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private static final String CONNECTION_STRING = "http://10.146.22.70:5050";

    @Test
    public void testGetLeaderSuccess() throws IOException, InterruptedException {
      String clusterJson = Resources.toString(
          MesosClientTest.class.getResource("/mesos_master_state.json"), Charsets.UTF_8);

      asyncHttpClient = HttpClientTestUtil.setupMocks(clusterJson, HttpStatus.SC_OK);
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getMasterLeader(CONNECTION_STRING, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String address) {
          assertEquals(address, CONNECTION_STRING);
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
    public void testWithoutIgnoringConnectionFailures() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocksToThrowInCallback(new SocketException("testing"));
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getMasterLeader(CONNECTION_STRING, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String address) {
          fail("getMasterState was expected to fail.");
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

  /**
   * Implements tests for the getNodeAddressesAsync method.
   */
  public class GetNodeAddressesAsyncTest {
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private static final String CONNECTION_STRING = "http://10.146.22.40:5050";

    private List<String> ipAddresses = Arrays.asList(
        "10.146.22.73", "10.146.22.74", "10.146.22.76");

    @Test
    public void testGetAddressesSuccess() throws IOException, InterruptedException {
      String clusterJson = Resources.toString(
          MesosClientTest.class.getResource("/mesos_master_state.json"), Charsets.UTF_8);

      asyncHttpClient = HttpClientTestUtil.setupMocks(clusterJson, HttpStatus.SC_OK);
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getNodeAddressesAsync(CONNECTION_STRING, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> addresses) {
          for (String address : ipAddresses) {
            assertTrue(addresses.contains(address));
          }
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
    public void testWithoutIgnoringConnectionFailures() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocksToThrowInCallback(new SocketException("testing"));
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getNodeAddressesAsync(CONNECTION_STRING, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> addresses) {
          fail("getMasterState was expected to fail.");
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

  /**
   * Implements tests for the getNodeNamesAsync method.
   */
  public class GetNodeNamesAsyncTest {
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private static final String CONNECTION_STRING = "http://10.146.22.40:5050";

    private List<String> hostnames = Arrays.asList(
        "worker-69b546aa-eab3-42d4-9196-a9d3b1786b0d",
        "worker-09461c65-7822-4c31-8f33-f10e2b6cb7e4",
        "worker-b717db52-8e2c-4924-bf73-4e7a7503bee4");

    @Test
    public void testGetAddressesSuccess() throws IOException, InterruptedException {
      String clusterJson = Resources.toString(
          MesosClientTest.class.getResource("/mesos_master_state.json"), Charsets.UTF_8);

      asyncHttpClient = HttpClientTestUtil.setupMocks(clusterJson, HttpStatus.SC_OK);
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getNodeNamesAsync(CONNECTION_STRING, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> names) {
          for (String hostname : hostnames) {
            assertTrue(names.contains(hostname));
          }
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
    public void testWithoutIgnoringConnectionFailures() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocksToThrowInCallback(new SocketException("testing"));
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getNodeNamesAsync(CONNECTION_STRING, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> addresses) {
          fail("getMasterState was expected to fail.");
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

  /**
   * Implements tests for the checkMarathon method.
   */
  public class CheckMarathonTest {
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private static final String CONNECTION_STRING = "http://10.146.22.70:5050";

    @Test
    public void testSuccess() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocks("", HttpStatus.SC_OK);
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.checkMarathon(CONNECTION_STRING, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(@Nullable Boolean success) {
          assertTrue(success);
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
    public void testFail() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocks("", HttpStatus.SC_BAD_REQUEST);
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.checkMarathon(CONNECTION_STRING, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(@Nullable Boolean success) {
          assertFalse(success);
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
    public void testWithoutIgnoringConnectionFailures() throws IOException, InterruptedException {
      asyncHttpClient = HttpClientTestUtil.setupMocksToThrowInCallback(new SocketException("testing"));
      MesosClient client = new MesosClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.checkMarathon(CONNECTION_STRING, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(@Nullable Boolean success) {
          fail("checkMarathon was expected to fail.");
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
