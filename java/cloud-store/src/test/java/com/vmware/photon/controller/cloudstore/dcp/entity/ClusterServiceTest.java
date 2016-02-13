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

import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.EnumSet;
import java.util.HashMap;

/**
 * Tests {@link ClusterService}.
 */
public class ClusterServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private ClusterService service;
  private ClusterService.State testState;

  private void buildValidStartState() throws Throwable {

    testState = new ClusterService.State();
    testState.clusterName = "clusterName";
    testState.clusterState = ClusterState.READY;
    testState.clusterType = ClusterType.KUBERNETES;
    testState.imageId = "imageId";
    testState.projectId = "projectId";
    testState.diskFlavorName = "diskFlavorName";
    testState.masterVmFlavorName = "masterVmFlavorName";
    testState.otherVmFlavorName = "otherVmFlavorName";
    testState.slaveCount = 3;
    testState.extendedProperties = new HashMap<>();
  }

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
      service = new ClusterService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);
      assertThat(service.getOptions(), is(expected));
    }
  }
  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = BasicServiceHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new ClusterService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      BasicServiceHost.destroy(host);
    }

    @Test
    public void testValidStartState() throws Throwable {
      buildValidStartState();
      Operation startOperation = host.startServiceSynchronously(service, testState);
      assertThat(startOperation.getStatusCode(), is(200));

      host.getServiceState(ClusterService.State.class);
    }
  }
}
