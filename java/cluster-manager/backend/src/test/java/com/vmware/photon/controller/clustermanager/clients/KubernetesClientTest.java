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
 * Tests {@link KubernetesClient}.
 */
public class KubernetesClientTest {
  private CloseableHttpAsyncClient asyncHttpClient;

  @Test
  private void dummy() {
  }

  /**
   * Implements tests for the getNodeAddressesAsync method.
   */
  public class GetNodeAddressesAsyncTest {
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private static final String CONNECTION_STRING = "http://10.146.22.40:8080";

    private List<String> ipAddresses = Arrays.asList(
        "10.146.22.40", "10.146.22.41", "10.146.22.42", "10.146.22.43");

    @Test
    public void testGetAddressesSuccess() throws IOException, InterruptedException {
      String clusterJson = Resources.toString(
          KubernetesClientTest.class.getResource("/kubernetes_cluster.json"), Charsets.UTF_8);

      asyncHttpClient = HttpClientTestUtil.setupMocks(clusterJson, HttpStatus.SC_OK);
      KubernetesClient client = new KubernetesClient(asyncHttpClient);

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
      KubernetesClient client = new KubernetesClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getNodeAddressesAsync(CONNECTION_STRING, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> addresses) {
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

  /**
   * Implements tests for the getVersionAsync method.
   */
  public class GetVersionAsyncTest {
    private static final String CONNECTION_STRING = "http://10.146.22.40:8080";
    private static final int LATCH_AWAIT_TIMEOUT = 10;
    private String testFileVersion = "v1.3.5";

    @Test
    public void testGetAddressesSuccess() throws IOException, InterruptedException {
      String clusterJson = Resources.toString(
          KubernetesClientTest.class.getResource("/kubernetes_version.json"), Charsets.UTF_8);

      asyncHttpClient = HttpClientTestUtil.setupMocks(clusterJson, HttpStatus.SC_OK);
      KubernetesClient client = new KubernetesClient(asyncHttpClient);

      final CountDownLatch latch = new CountDownLatch(1);
      client.getVersionAsync(CONNECTION_STRING, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String version) {
            assertTrue(version.equals(testFileVersion));
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
}
