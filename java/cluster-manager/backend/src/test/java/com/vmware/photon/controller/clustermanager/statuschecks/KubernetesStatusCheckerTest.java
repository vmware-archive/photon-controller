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

import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implements tests for {@link KubernetesStatusChecker}.
 */
public class KubernetesStatusCheckerTest {

  private static final int LATCH_AWAIT_TIMEOUT = 10;
  private static final String SERVER_ADDRESS = "10.0.0.1";

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private KubernetesClient setupMockKubernetesClient() throws Throwable {
    final Set<String> addresses = new HashSet<>();
    addresses.add("10.0.0.1");
    addresses.add("10.0.0.2");
    addresses.add("10.0.0.3");
    addresses.add("10.0.0.4");

    KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    doAnswer((InvocationOnMock invocation) -> {
      ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(addresses);
      return null;
    }).when(kubernetesClient).getNodeAddressesAsync(anyString(), any(FutureCallback.class));

    return kubernetesClient;
  }

  /**
   * Implements tests for the checkNodeStatus method.
   */
  public class CheckNodeStatusTest {

    @Test
    public void testKubernetesIsReady() throws Throwable {
      KubernetesClient kubernetesClient = setupMockKubernetesClient();
      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);

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
    public void testKubernetesIsNotReady() throws Throwable {
      KubernetesClient kubernetesClient = setupMockKubernetesClient();
      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);

      final CountDownLatch latch = new CountDownLatch(1);
      checker.checkNodeStatus("1.1.1.1", new FutureCallback<Boolean>() {
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
    public void testConnectionFailure() throws Throwable {
      KubernetesClient kubernetesClient = mock(KubernetesClient.class);
      doThrow(new ConnectException("Could not connect to Kubernetes"))
          .when(kubernetesClient).getNodeAddressesAsync(anyString(), any(FutureCallback.class));

      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);
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

  /**
   * Implements tests for the checkWorkersStatus method.
   */
  public class CheckWorkersStatus {

    @Test
    public void testWorkersAreReady() throws Throwable {
      KubernetesStatusChecker checker = new KubernetesStatusChecker(setupMockKubernetesClient());

      List<String> nodeAddresses = new ArrayList<>();
      nodeAddresses.add("10.0.0.1");

      final CountDownLatch latch = new CountDownLatch(1);
      checker.checkWorkersStatus(SERVER_ADDRESS, nodeAddresses, new FutureCallback<Boolean>() {
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
    public void testWorkersNotReady() throws Throwable {
      KubernetesClient kubernetesClient = setupMockKubernetesClient();
      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);

      List<String> nodeAddresses = new ArrayList<>();
      nodeAddresses.add("1.1.1.1");

      final CountDownLatch latch = new CountDownLatch(1);
      checker.checkWorkersStatus(SERVER_ADDRESS, nodeAddresses, new FutureCallback<Boolean>() {
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
    public void testConnectionFailure() throws Throwable {
      KubernetesClient kubernetesClient = mock(KubernetesClient.class);
      doThrow(new ConnectException("Could not connect to Kubernetes"))
          .when(kubernetesClient).getNodeAddressesAsync(anyString(), any(FutureCallback.class));

      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);
      List<String> nodeAddresses = new ArrayList<>();
      nodeAddresses.add("1.1.1.1");

      final CountDownLatch latch = new CountDownLatch(1);
      checker.checkWorkersStatus(SERVER_ADDRESS, nodeAddresses, new FutureCallback<Boolean>() {
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

  /**
   * Implements tests for the getWorkersStatus method.
   */
  public class GetWorkersTest {

    Set<String> hostnames;

    private KubernetesClient setupMockKubernetesClient() throws Throwable {
      hostnames = new HashSet<>();
      hostnames.add("worker-69b546aa-eab3-42d4-9196-a9d3b1786b0d");
      hostnames.add("worker-09461c65-7822-4c31-8f33-f10e2b6cb7e4");
      hostnames.add("worker-b717db52-8e2c-4924-bf73-4e7a7503bee4");

      KubernetesClient kubernetesClient = mock(KubernetesClient.class);
      doAnswer((InvocationOnMock invocation) -> {
        ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(hostnames);
        return null;
      }).when(kubernetesClient).getNodeNamesAsync(anyString(), any(FutureCallback.class));

      return kubernetesClient;
    }

    @Test
    public void testSuccess() throws Throwable {
      KubernetesClient kubernetesClient = setupMockKubernetesClient();
      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);

      final CountDownLatch latch = new CountDownLatch(1);
      checker.getWorkersStatus(SERVER_ADDRESS, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> set) {
          assertEquals(set, hostnames);
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
    public void testKubernetesConnectionFailure() throws Throwable {
      KubernetesClient kubernetesClient = mock(KubernetesClient.class);
      doThrow(new ConnectException("Could not connect to Kubernetes"))
          .when(kubernetesClient).getNodeNamesAsync(anyString(), any(FutureCallback.class));

      KubernetesStatusChecker checker = new KubernetesStatusChecker(kubernetesClient);

      final CountDownLatch latch = new CountDownLatch(1);
      checker.getWorkersStatus(SERVER_ADDRESS, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> set) {
          fail();
          latch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          latch.countDown();
        }
      });

      assertThat(latch.await(LATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
    }
  }
}
