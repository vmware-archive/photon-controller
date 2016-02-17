/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

/**
 * This class implements tests for the {@link NetworkService} class.
 */
public class NetworkServiceTest {

  private NetworkService.NetworkState buildValidStartState() {
    NetworkService.NetworkState networkState = new NetworkService.NetworkState();
    networkState.id = UUID.randomUUID().toString();
    networkState.name = "networkName";
    networkState.subnetCIDR = "10.0.0.0/10";
    networkState.tenantLinks = new ArrayList<>();
    networkState.tenantLinks.add("tenant-linkA");
    networkState.regionID = "regionID";
    networkState.authCredentialsLink = "http://authCredentialsLink";
    networkState.resourcePoolLink = "http://resourcePoolLink";
    try {
        networkState.instanceAdapterReference = new URI("http://instanceAdapterReference");
    } catch (Exception e) {
        networkState.instanceAdapterReference = null;
    }

    return networkState;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {
    private NetworkService networkService = new NetworkService();

    @BeforeMethod
    public void setupTest() {
      networkService = new NetworkService();
    }

    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);
      assertThat(networkService.getOptions(), is(expected));
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
      NetworkService.NetworkState startState = buildValidStartState();
      NetworkService.NetworkState returnState = host.postServiceSynchronously(
          NetworkFactoryService.SELF_LINK,
          startState,
          NetworkService.NetworkState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
      assertThat(returnState.tenantLinks.get(0), is(startState.tenantLinks.get(0)));
      assertThat(returnState.regionID, is(startState.regionID));
      assertThat(returnState.authCredentialsLink, is(startState.authCredentialsLink));
      assertThat(returnState.resourcePoolLink, is(startState.resourcePoolLink));
      assertThat(returnState.instanceAdapterReference, is(startState.instanceAdapterReference));

    }

    @DataProvider(name = "createInvalidValues")
    public Object[][] createInvalidValues() {
      NetworkService.NetworkState missingSubnet = buildValidStartState();
      NetworkService.NetworkState invalidSubnet1 = buildValidStartState();
      NetworkService.NetworkState invalidSubnet2 = buildValidStartState();
      NetworkService.NetworkState invalidSubnet3 = buildValidStartState();
      NetworkService.NetworkState invalidSubnet4 = buildValidStartState();
      NetworkService.NetworkState invalidSubnet5 = buildValidStartState();

      missingSubnet.subnetCIDR = null;
      // no subnet
      invalidSubnet1.subnetCIDR = "10.0.0.0";
      // invalid IP
      invalidSubnet2.subnetCIDR = "10.0.0.A";
      // invalid Subnet range
      invalidSubnet3.subnetCIDR = "10.0.0.0/33";
      // invalid Subnet range
      invalidSubnet4.subnetCIDR = "10.0.0.0/-1";
      // invalid Subnet separator
      invalidSubnet5.subnetCIDR = "10.0.0.0\\0";

      return new Object[][]{
          {missingSubnet},
          {invalidSubnet1},
          {invalidSubnet2},
          {invalidSubnet3},
          {invalidSubnet4},
          {invalidSubnet5},
      };
    }

    @Test(dataProvider = "createInvalidValues")
    public void testMissingValue(NetworkService.NetworkState startState) throws Throwable {
      host.postServiceSynchronously(
          NetworkFactoryService.SELF_LINK,
          startState,
          NetworkService.NetworkState.class, IllegalArgumentException.class);
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
      NetworkService.NetworkState startState = buildValidStartState();

      NetworkService.NetworkState returnState = host.postServiceSynchronously(
          NetworkFactoryService.SELF_LINK,
          startState,
          NetworkService.NetworkState.class);

      NetworkService.NetworkState patchState = new NetworkService.NetworkState();
      patchState.name = "patchNetworkName";
      patchState.subnetCIDR = "152.151.150.222/22";
      patchState.customProperties = new HashMap<>();
      patchState.customProperties.put("patchKey", "patchValue");
      patchState.regionID = "patchRregionID";
      patchState.authCredentialsLink = "http://patchAuthCredentialsLink";
      patchState.resourcePoolLink = "http://patchResourcePoolLink";
      try {
        patchState.instanceAdapterReference = new URI("http://patchInstanceAdapterReference");
      } catch (Exception e) {
        patchState.instanceAdapterReference = null;
      }


      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);

      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          NetworkService.NetworkState.class);

      assertThat(returnState.name, is(patchState.name));
      assertThat(returnState.subnetCIDR, is(patchState.subnetCIDR));
      assertThat(returnState.customProperties, is(patchState.customProperties));
      assertThat(returnState.regionID, is(patchState.regionID));
      assertThat(returnState.authCredentialsLink, is(patchState.authCredentialsLink));
      assertThat(returnState.resourcePoolLink, is(patchState.resourcePoolLink));
      assertThat(returnState.instanceAdapterReference, is(patchState.instanceAdapterReference));

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
      NetworkService.NetworkState networkState = buildValidStartState();
      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      networkState.tenantLinks = new ArrayList<>();
      networkState.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));
      NetworkService.NetworkState startState = host.postServiceSynchronously(
          NetworkFactoryService.SELF_LINK,
          networkState,
          NetworkService.NetworkState.class);

      String kind = Utils.buildKind(NetworkService.NetworkState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, networkState.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }

}
