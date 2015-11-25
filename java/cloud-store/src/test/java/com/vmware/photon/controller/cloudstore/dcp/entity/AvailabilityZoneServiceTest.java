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

import com.vmware.photon.controller.api.AvailabilityZoneState;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneService}.
 */
public class AvailabilityZoneServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private AvailabilityZoneService service;
  private AvailabilityZoneService.State testAvailabilityZone;

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
      service = new AvailabilityZoneService();
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
      service = new AvailabilityZoneService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          AvailabilityZoneServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testAvailabilityZone = new AvailabilityZoneService.State();
      testAvailabilityZone.name = "dummyName";
      testAvailabilityZone.state = AvailabilityZoneState.READY;
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
      host.startServiceSynchronously(new AvailabilityZoneServiceFactory(), null);

      Operation result = dcpRestClient.post(AvailabilityZoneServiceFactory.SELF_LINK, testAvailabilityZone);

      assertThat(result.getStatusCode(), is(200));
      AvailabilityZoneService.State createdState = result.getBody(AvailabilityZoneService.State.class);
      assertThat(createdState.name, is(testAvailabilityZone.name));
      AvailabilityZoneService.State savedState = host.getServiceState(AvailabilityZoneService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(testAvailabilityZone.name));
    }

    /**
     * Test service start with missing availabilityZone in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingAvailabilityZone() throws Throwable {
      AvailabilityZoneService.State startState = new AvailabilityZoneService.State();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'availabilityZone.name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new AvailabilityZoneService();
      host = BasicServiceHost.create();
      testAvailabilityZone = new AvailabilityZoneService.State();
      testAvailabilityZone.name = "dummyName";
      testAvailabilityZone.state = AvailabilityZoneState.READY;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation completed.
     *
     * @throws Throwable
     */
    @Test
    public void testPatch() throws Throwable {
      host.startServiceSynchronously(service, testAvailabilityZone);

      AvailabilityZoneService.State patchAvailabilityZone = new AvailabilityZoneService.State();
      patchAvailabilityZone.state = AvailabilityZoneState.PENDING_DELETE;
      patchAvailabilityZone.deleteRequestTime = System.currentTimeMillis();

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchAvailabilityZone);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(200));
      AvailabilityZoneService.State patchedState = result.getBody(AvailabilityZoneService.State.class);
      AvailabilityZoneService.State savedState = host.getServiceState(AvailabilityZoneService.State.class,
          patchedState.documentSelfLink);
      assertThat(savedState.name, is(testAvailabilityZone.name));
      assertThat(savedState.state, is(AvailabilityZoneState.PENDING_DELETE));
      assertThat(savedState.deleteRequestTime, is(patchAvailabilityZone.deleteRequestTime));
    }

    /**
     * Test patch operation  with no deleteRequestTime filed.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchState() throws Throwable {
      host.startServiceSynchronously(service, testAvailabilityZone);

      AvailabilityZoneService.State patchAvailabilityZone = new AvailabilityZoneService.State();
      patchAvailabilityZone.state = AvailabilityZoneState.DELETED;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchAvailabilityZone);

      try {
        host.sendRequestAndWait(patch);
        fail("should have failed with IllegalStateException");
      } catch (BadRequestException e) {
      }
    }

    /**
     * Test patch operation failed with no deleteRequestTime filed.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchDeleteRequestTime() throws Throwable {
      host.startServiceSynchronously(service, testAvailabilityZone);

      AvailabilityZoneService.State patchAvailabilityZone = new AvailabilityZoneService.State();
      patchAvailabilityZone.state = AvailabilityZoneState.PENDING_DELETE;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchAvailabilityZone);

      try {
        host.sendRequestAndWait(patch);
        fail("should have failed with NullPointerException");
      } catch (DcpRuntimeException e) {
      }
    }
  }
}
