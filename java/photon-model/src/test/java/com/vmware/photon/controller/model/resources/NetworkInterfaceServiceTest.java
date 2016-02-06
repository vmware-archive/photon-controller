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


import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantFactoryService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
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
    networkInterfaceState.address = "10.0.0.0";
    networkInterfaceState.networkBridgeLink = "network-bridge-link";
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
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(networkInterfaceService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testValidStartState() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.address, is(startState.address));
      assertThat(returnState.networkBridgeLink, is(startState.networkBridgeLink));
    }

    @Test
    public void testMissingId() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      startState.id = null;

      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test
    public void testMissingBody() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          null,
          NetworkInterfaceService.NetworkInterfaceState.class, IllegalArgumentException.class);
    }

    @Test
    public void testInvalidAddress() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      startState.address = "bad-ip-address";
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class, IllegalArgumentException.class);
    }

    @Test
    public void testMissingAddressAndDescriptionLink() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      startState.address = null;
      startState.networkDescriptionLink = null;
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class, IllegalArgumentException.class);
    }

    @Test
    public void testHavingAddressAndDescriptionLink() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState startState = buildValidStartState();
      startState.address = "10.0.0.1";
      startState.networkDescriptionLink = "10.0.0.2";
      NetworkInterfaceService.NetworkInterfaceState returnState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          startState,
          NetworkInterfaceService.NetworkInterfaceState.class, IllegalArgumentException.class);
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
      patchState.address = "10.0.0.1";
      patchState.leaseLink = "10.0.0.2";
      patchState.networkBridgeLink = "10.0.0.3";
      patchState.networkDescriptionLink = "10.0.0.4";
      patchState.tenantLinks = new ArrayList<>();
      patchState.tenantLinks.add("tenant-linkA");

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);

      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          NetworkInterfaceService.NetworkInterfaceState.class);

      assertThat(returnState.leaseLink, is(patchState.leaseLink));
      // Only leaselink is patched others are not updated
      assertThat(returnState.address, is(startState.address));
      assertThat(returnState.networkBridgeLink, is(startState.networkBridgeLink));
      assertThat(returnState.networkDescriptionLink, is(startState.networkDescriptionLink));
    }
  }

  /**
   * This class implements tests for query.
   */
  public class QueryTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testTenantLinksQuery() throws Throwable {
      NetworkInterfaceService.NetworkInterfaceState nic = buildValidStartState();
      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      nic.tenantLinks = new ArrayList<>();
      nic.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));
      NetworkInterfaceService.NetworkInterfaceState startState = host.postServiceSynchronously(
          NetworkInterfaceFactoryService.SELF_LINK,
          nic,
          NetworkInterfaceService.NetworkInterfaceState.class);

      String kind = Utils.buildKind(NetworkInterfaceService.NetworkInterfaceState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, nic.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }
}
