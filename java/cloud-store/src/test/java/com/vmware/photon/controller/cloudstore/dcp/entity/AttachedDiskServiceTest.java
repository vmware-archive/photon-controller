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

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

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
 * Tests {@link AttachedDiskService}.
 */
public class AttachedDiskServiceTest {

  private DcpRestClient dcpRestClient;
  private BasicServiceHost host;
  private AttachedDiskService service;
  private AttachedDiskService.State testState;

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
      service = new AttachedDiskService();
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
      service = new AttachedDiskService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          AttachedDiskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new DcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new AttachedDiskService.State();
      testState.bootDisk = true;
      testState.kind = EphemeralDisk.KIND;
      testState.vmId = "vm-id";
      testState.persistentDiskId = "disk-id";
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
      host.startServiceSynchronously(new AttachedDiskServiceFactory(), null);

      Operation result = dcpRestClient.post(AttachedDiskServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      AttachedDiskService.State createdState = result.getBody(AttachedDiskService.State.class);
      assertThat(createdState.bootDisk, is(testState.bootDisk));
      assertThat(createdState.kind, is(EphemeralDisk.KIND));
      assertThat(createdState.vmId, is(testState.vmId));
      assertThat(createdState.persistentDiskId, is(testState.persistentDiskId));

      AttachedDiskService.State savedState =
          host.getServiceState(AttachedDiskService.State.class, createdState.documentSelfLink);
      assertThat(savedState.bootDisk, is(testState.bootDisk));
      assertThat(savedState.kind, is(testState.kind));
      assertThat(savedState.vmId, is(testState.vmId));
      assertThat(savedState.persistentDiskId, is(testState.persistentDiskId));
    }

    /**
     * Test service start with missing vmId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingVmId() throws Throwable {
      AttachedDiskService.State startState = new AttachedDiskService.State();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'vmId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("vmId cannot be null"));
      }
    }
  }

}
