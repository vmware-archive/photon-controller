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

package com.vmware.photon.controller.housekeeper.service;


import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.SimpleServiceNode;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerReader;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServiceReader;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;
import com.vmware.xenon.common.UriUtils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link HousekeeperService}.
 */
public class HousekeeperServiceTest {

  private HousekeeperService service;
  private ServerSet serverSet;
  private HousekeeperXenonServiceHost host;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the replicateImage method.
   */
  public class ReplicateImageTest {
    @BeforeClass
    private void setUpClass() {
      serverSet = mock(ServerSet.class);
      host = mock(HousekeeperXenonServiceHost.class);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      service = spy(new HousekeeperService(serverSet, host));
    }

    @Test
    public void testInvocation() throws Throwable {
      ImageReplicator replicator = mock(ImageReplicator.class);
      doReturn(replicator).when(service).buildReplicator();

      ReplicateImageResponse response = new ReplicateImageResponse(new ReplicateImageResult(ReplicateImageResultCode
          .OK));
      response.setOperation_id("opID");
      doReturn(response).when(replicator).replicateImage(any(ReplicateImageRequest.class));
      assertThat(service.replicate_image(new ReplicateImageRequest()), is(response));
    }

    @Test
    public void testInvocationWithGivenRequestId() throws Throwable {
      ImageReplicator replicator = mock(ImageReplicator.class);
      doReturn(replicator).when(service).buildReplicator();

      ReplicateImageResponse response = new ReplicateImageResponse(new ReplicateImageResult(ReplicateImageResultCode
          .OK));
      doReturn(response).when(replicator).replicateImage(any(ReplicateImageRequest.class));

      // When given no request id, verifying a request id is still created in MDC context.
      MDC.remove("request");
      assertThat(service.replicate_image(new ReplicateImageRequest()), is(response));
      assertThat(MDC.get("request"), is(notNullValue()));

      // When given request id, verifying the same id is passed into MDC context.
      String givenRequestId = "GivenRequestId";
      ReplicateImageRequest requestWithId = new ReplicateImageRequest();
      TracingInfo traceInfoWithRequestId = new TracingInfo();
      traceInfoWithRequestId.setRequest_id(givenRequestId);
      requestWithId.setTracing_info(traceInfoWithRequestId);
      assertThat(service.replicate_image(requestWithId), is(response));
      assertThat(MDC.get("request"), is(String.format(" [Req: %s]", givenRequestId)));
    }
  }

  /**
   * Tests for the info method.
   */
  public class GetStatusTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      serverSet = mock(ServerSet.class);
      host = mock(HousekeeperXenonServiceHost.class);
      service = new HousekeeperService(serverSet, host);
    }

    @Test
    public void testInitializing() throws Throwable {
      assertThat(service.get_status().getType(), is(StatusType.INITIALIZING));
    }

    @Test
    public void testReady() throws Throwable {
      doReturn(true).when(host).isReady();
      assertThat(service.get_status().getType(), is(StatusType.READY));
    }
  }

  /**
   * Tests for logic in zookeeper registration.
   */
  public class ZookeeperRegistrationTest {

    private final InetSocketAddress address1 = new InetSocketAddress("192.168.1.1", 16000);
    private final InetSocketAddress address2 = new InetSocketAddress("192.168.1.2", 16000);

    private CuratorFramework zkClient;
    private ServerSet serverSet;

    @BeforeMethod
    public void setUp() throws Throwable {
      TestingServer zookeeper = new TestingServer();
      Timing timing = new Timing();

      zkClient = CuratorFrameworkFactory
          .newClient(zookeeper.getConnectString(),
              timing.session(),
              timing.connection(),
              new RetryOneTime(1));
      zkClient.start();

      ZookeeperServerReader reader = new ZookeeperServiceReader();
      serverSet = new ZookeeperServerSet(
          new PathChildrenCacheFactory(zkClient, reader),
          zkClient, reader, "housekeeper", true);
    }

    @AfterMethod
    public void tearDown() {
      if (zkClient != null) {
        zkClient.close();
      }
    }

    @Test
    public void testJoin() throws Throwable {
      TestGroup testGroup1 = createTestGroup(address1.getHostName(), address1.getPort());
      TestGroup testGroup2 = createTestGroup(address2.getHostName(), address2.getPort());

      // join first node
      CountDownLatch done = new CountDownLatch(4);
      testGroup1.houseKeeperService.setCountDownLatch(done);
      testGroup2.houseKeeperService.setCountDownLatch(done);
      testGroup1.node.join();
      testGroup2.node.join();

      assertTrue(done.await(5, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertThat(testGroup1.houseKeeperService.getServers(), containsInAnyOrder(address1, address2));
      assertThat(testGroup2.houseKeeperService.getServers(), containsInAnyOrder(address1, address2));
    }

    private TestGroup createTestGroup(String hostname, int port) {
      ServiceNode node =
          new SimpleServiceNode(zkClient, "housekeeper", new InetSocketAddress(hostname, port));

      HousekeeperXenonServiceHost dcpHost = mock(HousekeeperXenonServiceHost.class);
      when(dcpHost.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP)).thenReturn(true);
      when(dcpHost.getUri()).thenReturn(UriUtils.buildUri(hostname, port + 1, "", null));

      TestHouseKeeperService housekeeperService =
          new TestHouseKeeperService(serverSet, dcpHost);
      serverSet.addChangeListener(housekeeperService);

      return new TestGroup(node, housekeeperService);
    }

    private class TestGroup {
      private ServiceNode node; // zookeeper node
      private TestHouseKeeperService houseKeeperService;

      private TestGroup(ServiceNode node,
                        TestHouseKeeperService houseKeeperService) {
        this.node = node;
        this.houseKeeperService = houseKeeperService;
      }
    }

    private class TestHouseKeeperService extends HousekeeperService {

      private CountDownLatch countDownLatch;

      public TestHouseKeeperService(ServerSet serverSet,
                                    HousekeeperXenonServiceHost host) {
        super(serverSet, host);
      }

      public void setCountDownLatch(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
      }

      @Override
      public void onServerAdded(InetSocketAddress address) {
        super.onServerAdded(address);
        assertThat(countDownLatch, notNullValue());
        countDownLatch.countDown();
      }
    }
  }
}
