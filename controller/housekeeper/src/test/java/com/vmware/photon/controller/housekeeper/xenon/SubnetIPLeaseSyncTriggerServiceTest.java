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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.housekeeper.helpers.xenon.TestEnvironment;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link SubnetIPLeaseSyncTriggerService}.
 */
public class SubnetIPLeaseSyncTriggerServiceTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link SubnetIPLeaseSyncTriggerService#handlePost(com.vmware.xenon.common.Operation)}.
   */
  public class HandlePostTest {

    private static final int TEST_PAGE_LIMIT = 100;
    private TestEnvironment machine;
    private TestEnvironment.Builder machineBuilder;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private SubnetIPLeaseSyncService.State request;

    @BeforeMethod
    public void setUp() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreHelper = new CloudStoreHelper();

      machineBuilder = new TestEnvironment.Builder()
              .cloudStoreHelper(cloudStoreHelper)
              .hostClientFactory(hostClientFactory);
      // Build input.
      request = buildSubnetIPLeaseSyncState("subnetId");
      request.isSelfProgressionDisabled = false;
      request.pageLimit = TEST_PAGE_LIMIT;
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      machine.stop();
      machine = null;
    }

    @Test(dataProvider = "hostCount")
    public void testPost(int hostCount) throws Throwable {
      machine = machineBuilder
              .hostCount(hostCount)
              .build();
      Operation operation = Operation.createPost(machine.getHosts()[0],
              SubnetIPLeaseSyncTriggerService.SELF_LINK)
              .setBody(request);
      machine.getHosts()[0].sendRequest(operation);
    }

    /**
     * Default provider to control host count.
     *
     * @return
     */
    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
              {1},
              {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    /**
     * Initializes a start state for SubnetIPLeaseSyncService.
     *
     * @param subnetId
     * */
    public SubnetIPLeaseSyncService.State buildSubnetIPLeaseSyncState(String subnetId)  {
      SubnetIPLeaseSyncService.State state = new SubnetIPLeaseSyncService.State();
      state.subnetId = subnetId;
      state.taskState = new SubnetIPLeaseSyncService.TaskState();
      state.taskState.stage = SubnetIPLeaseSyncService.TaskState.TaskStage.CREATED;

      return state;
    }
  }
}
