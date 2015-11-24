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

package com.vmware.photon.controller.model.resources;


import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link NetworkInterfaceService} class.
 */
public class NetworkInterfaceServiceTest {

  private NetworkInterfaceService.NetworkInterfaceState buildValidStartState() {
    NetworkInterfaceService.NetworkInterfaceState networkInterfaceState =
        new NetworkInterfaceService.NetworkInterfaceState();
    networkInterfaceState.id = UUID.randomUUID().toString();
    return networkInterfaceState;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {
    private NetworkInterfaceService networkInterfaceService;

    @BeforeMethod
    public void setupTest() {
      networkInterfaceService = new NetworkInterfaceService();
    }

    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(networkInterfaceService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testPatch() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();

      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class);

      NetworkInterfaceService.NetworkInterfaceState patchState = new NetworkInterfaceService.NetworkInterfaceState();
      patchState.id = UUID.randomUUID().toString();
      patchState.address = "10.0.0.1";
      patchState.leaseLink = "10.0.0.2";
      patchState.networkBridgeLink = "10.0.0.3";
      patchState.networkDescriptionLink = "10.0.0.4";
      patchState.tenantLinks = new ArrayList<>();

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState
      );

      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          NetworkInterfaceService.NetworkInterfaceState.class
      );

      assertThat(returnState.id, is(patchState.id));
      assertThat(returnState.leaseLink, is(patchState.leaseLink));
      // Only id and leaselink are patched others are not updated
      assertThat(returnState.address, is(nullValue()));
      assertThat(returnState.networkBridgeLink, is(nullValue()));
      assertThat(returnState.networkDescriptionLink, is(nullValue()));
      assertThat(returnState.tenantLinks, is(nullValue()));
    }

    @Test
    public void testPatchNoChange() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class
      );
      assertNotNull(returnState);

      NetworkInterfaceService.NetworkInterfaceState patchState = new NetworkInterfaceService.NetworkInterfaceState();
      host.patchServiceSynchronously(returnState.documentSelfLink, patchState);

      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          NetworkInterfaceService.NetworkInterfaceState.class
      );

      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.leaseLink, is(startState.leaseLink));
      assertThat(returnState.address, is(startState.address));
      assertThat(returnState.networkDescriptionLink, is(startState.networkDescriptionLink));
      assertThat(returnState.networkBridgeLink, is(startState.networkBridgeLink));
      assertThat(returnState.tenantLinks, is(startState.tenantLinks));
    }
  }
}
