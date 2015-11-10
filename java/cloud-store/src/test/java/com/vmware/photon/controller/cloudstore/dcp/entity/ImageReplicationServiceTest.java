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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationService}.
 */
public class ImageReplicationServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private ImageReplicationService service;
  private ImageReplicationService.State testState;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new ImageReplicationService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageReplicationService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          ImageReplicationServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ImageReplicationService.State();
      testState.imageId = UUID.randomUUID().toString();
      testState.imageDatastoreId = UUID.randomUUID().toString();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new ImageReplicationServiceFactory(), null);

      Operation result = dcpRestClient.postAndWait(ImageReplicationServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      ImageReplicationService.State createdState = result.getBody(ImageReplicationService.State.class);
      assertThat(createdState.imageId, is(equalTo(testState.imageId)));
      assertThat(createdState.imageDatastoreId, is(equalTo(testState.imageDatastoreId)));
      ImageReplicationService.State savedState =
          host.getServiceState(ImageReplicationService.State.class, createdState.documentSelfLink);
      assertThat(savedState.imageId, is(equalTo(testState.imageId)));
      assertThat(savedState.imageDatastoreId, is(equalTo(testState.imageDatastoreId)));
    }

    /**
     * Test service start with missing imageId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImageId() throws Throwable {
      ImageReplicationService.State startState = new ImageReplicationService.State();
      startState.imageDatastoreId = "image-datastore-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'imageId' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("imageId cannot be null"));
      }
    }

    /**
     * Test service start with missing imageDatastoreId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImageDatastoreId() throws Throwable {
      ImageReplicationService.State startState = new ImageReplicationService.State();
      startState.imageId = "image-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'imageDatastoreId' was null");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("imageDatastoreId cannot be null"));
      }
    }
  }

}
