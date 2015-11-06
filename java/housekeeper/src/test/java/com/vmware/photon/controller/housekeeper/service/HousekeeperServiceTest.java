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


import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.SimpleServiceNode;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerReader;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServiceReader;
import com.vmware.photon.controller.housekeeper.dcp.DcpConfig;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResult;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatus;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link HousekeeperService}.
 */
public class HousekeeperServiceTest {

  private HousekeeperService service;
  private Injector injector;

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
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector("/config.yml");
      service = spy(injector.getInstance(HousekeeperService.class));
    }

    @Test
    public void testInvocation() throws Throwable {
      ImageReplicator replicator = spy(new ImageReplicator(injector.getInstance(HousekeeperDcpServiceHost.class), 10));
      doReturn(replicator).when(service).buildReplicator();

      ReplicateImageResponse response = new ReplicateImageResponse(new ReplicateImageResult(ReplicateImageResultCode
          .OK));
      response.setOperation_id("opID");
      doReturn(response).when(replicator).replicateImage(any(ReplicateImageRequest.class));
      assertThat(service.replicate_image(new ReplicateImageRequest()), is(response));
    }

    @Test
    public void testInvocationWithGivenRequestId() throws Throwable {
      ImageReplicator replicator = spy(new ImageReplicator(injector.getInstance(HousekeeperDcpServiceHost.class), 10));
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
   * Tests for the remove_image method.
   */
  public class RemoveImageTest {
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector("/config.yml");
      service = spy(injector.getInstance(HousekeeperService.class));

      // clear MDC
      MDC.remove("request");
    }

    @Test
    public void testSuccess() throws Throwable {
      ImageRemover remover =
          spy(new ImageRemover(injector.getInstance(HousekeeperDcpServiceHost.class)));
      doReturn(remover).when(service).buildReplicasRemover();

      RemoveImageResponse response = new RemoveImageResponse(new RemoveImageResult(RemoveImageResultCode.OK));
      response.setOperation_id("opID");
      doReturn(response).when(remover).removeImage(any(RemoveImageRequest.class));
      assertThat(service.remove_image(new RemoveImageRequest()), is(response));
    }

    @Test
    public void testNullRequestObject() throws Throwable {
      try {
        service.remove_image(null);
        fail("remove_image should have raised exception on null input");
      } catch (NullPointerException ex) {
        assertThat(ex.getMessage(), is("request cannot be null"));
      }
    }

    @Test
    public void testRequestIdIsPropagated() throws Throwable {
      String givenRequestId = "GivenRequestId";

      RemoveImageRequest request = new RemoveImageRequest();
      TracingInfo traceInfo = new TracingInfo();
      traceInfo.setRequest_id(givenRequestId);
      request.setTracing_info(traceInfo);

      assertThat(service.remove_image(request), isA(RemoveImageResponse.class));
      assertThat(MDC.get("request"), is(String.format(" [Req: %s]", givenRequestId)));
    }

    @Test
    public void testRequestIdIsGenerated() throws Throwable {
      assertThat(service.remove_image(new RemoveImageRequest()), isA(RemoveImageResponse.class));
      assertThat(MDC.get("request"), startsWith(" [Req: "));
    }
  }

  /**
   * Tests for the remove_image_status method.
   */
  public class RemoveImageStatusTest {
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector("/config.yml");
      service = spy(injector.getInstance(HousekeeperService.class));

      // clear MDC
      MDC.remove("request");
    }

    @Test
    public void testSuccess() throws Throwable {
      ImageRemover remover =
          spy(new ImageRemover(injector.getInstance(HousekeeperDcpServiceHost.class)));
      doReturn(remover).when(service).buildReplicasRemover();

      RemoveImageStatusResponse response =
          new RemoveImageStatusResponse(new RemoveImageResult(RemoveImageResultCode.OK));
      response.setStatus(new RemoveImageStatus(RemoveImageStatusCode.FINISHED));
      doReturn(response).when(remover).getImageRemovalStatus(any(RemoveImageStatusRequest.class));
      assertThat(service.remove_image_status(new RemoveImageStatusRequest()), is(response));
    }

    @Test
    public void testNullRequestObject() throws Throwable {
      try {
        service.remove_image_status(null);
        fail("remove_image should have raised exception on null input");
      } catch (NullPointerException ex) {
        assertThat(ex.getMessage(), is("request cannot be null"));
      }
    }

    @Test
    public void testRequestIdIsPropagated() throws Throwable {
      String givenRequestId = "GivenRequestId";

      RemoveImageStatusRequest request = new RemoveImageStatusRequest();
      TracingInfo traceInfo = new TracingInfo();
      traceInfo.setRequest_id(givenRequestId);
      request.setTracing_info(traceInfo);

      assertThat(service.remove_image_status(request), isA(RemoveImageStatusResponse.class));
      assertThat(MDC.get("request"), is(String.format(" [Req: %s]", givenRequestId)));
    }

    @Test
    public void testRequestIdIsGenerated() throws Throwable {
      assertThat(service.remove_image_status(new RemoveImageStatusRequest()), isA(RemoveImageStatusResponse.class));
      assertThat(MDC.get("request"), startsWith(" [Req: "));
    }
  }

  /**
   * Tests for the info method.
   */
  public class GetStatusTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector("/config.yml");
      service = injector.getInstance(HousekeeperService.class);
    }

    @Test
    public void testInitializing() throws Throwable {
      assertThat(service.get_status().getType(), is(StatusType.INITIALIZING));
    }

    @Test
    public void testReady() throws Throwable {
      HousekeeperDcpServiceHost dcpHost = injector.getInstance(HousekeeperDcpServiceHost.class);

      doReturn(true).when(dcpHost).isReady();
      assertThat(service.get_status().getType(), is(StatusType.READY));
    }
  }

  /**
   * Tests for logic in zookeeper registration.
   */
  public class ZookeeperRegistrationTest {

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
      int port = 16000;
      TestGroup testGroup1 = createTestGroup("192.168.1.1", port);
      TestGroup testGroup2 = createTestGroup("192.168.1.2", port);

      // join first node
      CountDownLatch done = new CountDownLatch(2);
      testGroup1.houseKeeperService.setCountDownLatch(done);
      testGroup2.houseKeeperService.setCountDownLatch(done);
      testGroup1.node.join();

      assertTrue(done.await(5, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertEquals(testGroup1.houseKeeperService.getServers().size(), 1);
      assertEquals(testGroup2.houseKeeperService.getServers().size(), 1);

      // join second node
      done = new CountDownLatch(2);
      testGroup1.houseKeeperService.setCountDownLatch(done);
      testGroup2.houseKeeperService.setCountDownLatch(done);

      testGroup2.node.join();
      assertTrue(done.await(5, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertEquals(testGroup1.houseKeeperService.getServers().size(), 2);
      assertEquals(testGroup2.houseKeeperService.getServers().size(), 2);

      verify(testGroup1.dcpHost).checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP);
      verify(testGroup2.dcpHost).checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP);

      verify(testGroup1.dcpHost, times(2)).getUri();
      verify(testGroup2.dcpHost, times(2)).getUri();
      verify(testGroup1.dcpHost).getPort();
      verify(testGroup2.dcpHost).getPort();
      verify(testGroup2.dcpHost).joinPeers(
          ImmutableList.of(UriUtils.buildUri("192.168.1.1", 0, "", null)),
          ServiceUriPaths.DEFAULT_NODE_GROUP);
      verify(testGroup1.dcpHost).joinPeers(
          ImmutableList.of(UriUtils.buildUri("192.168.1.2", 0, "", null)),
          ServiceUriPaths.DEFAULT_NODE_GROUP);
      verifyNoMoreInteractions(testGroup1.dcpHost, testGroup2.dcpHost);
    }

    private TestGroup createTestGroup(String hostname, int port) {
      ServiceNode node =
          new SimpleServiceNode(zkClient, "housekeeper", new InetSocketAddress(hostname, port));

      HousekeeperDcpServiceHost dcpHost = mock(HousekeeperDcpServiceHost.class);
      when(dcpHost.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP)).thenReturn(true);
      when(dcpHost.getUri()).thenReturn(UriUtils.buildUri(hostname, port + 1, "", null));

      TestHouseKeeperService housekeeperService =
          new TestHouseKeeperService(serverSet, dcpHost, mock(DcpConfig.class), mock(BuildInfo.class));
      serverSet.addChangeListener(housekeeperService);

      return new TestGroup(node, dcpHost, housekeeperService);
    }

    private class TestGroup {
      private ServiceNode node; // zookeeper node
      private HousekeeperDcpServiceHost dcpHost; // DcpHost
      private TestHouseKeeperService houseKeeperService;

      private TestGroup(ServiceNode node,
                        HousekeeperDcpServiceHost dcpHost,
                        TestHouseKeeperService houseKeeperService) {
        this.node = node;
        this.dcpHost = dcpHost;
        this.houseKeeperService = houseKeeperService;
      }
    }

    private class TestHouseKeeperService extends HousekeeperService {

      private CountDownLatch countDownLatch;

      public TestHouseKeeperService(ServerSet serverSet,
                                    HousekeeperDcpServiceHost host,
                                    DcpConfig dcpConfig,
                                    BuildInfo buildInfo) {
        super(serverSet, host, dcpConfig, buildInfo);
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
