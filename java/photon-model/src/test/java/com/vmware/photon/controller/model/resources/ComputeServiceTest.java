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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

/**
 * This class implements tests for the {@link ComputeService} class.
 */
public class ComputeServiceTest {
  private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
  private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID().toString();

  public static ComputeService.ComputeStateWithDescription buildValidStartState(
      ComputeDescriptionService.ComputeDescription cd) throws Throwable {
    ComputeService.ComputeStateWithDescription cs = new ComputeService.ComputeStateWithDescription();
    cs.id = UUID.randomUUID().toString();
    cs.description = cd;
    cs.descriptionLink = cd.documentSelfLink;
    cs.resourcePoolLink = null;
    cs.address = "10.0.0.1";
    cs.primaryMAC = "01:23:45:67:89:ab";
    cs.powerState = ComputeService.PowerState.ON;
    cs.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");
    cs.diskLinks = new ArrayList<>();
    cs.diskLinks.add("http://disk");
    cs.networkLinks = new ArrayList<>();
    cs.networkLinks.add("http://network");
    cs.customProperties = new HashMap<>();
    cs.customProperties.put(TEST_DESC_PROPERTY_NAME, TEST_DESC_PROPERTY_VALUE);
    return cs;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ComputeService computeService;

    @BeforeMethod
    public void setUpTest() {
      computeService = new ComputeService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(computeService.getOptions(), is(expected));
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
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = ComputeServiceTest.buildValidStartState(cd);
      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.descriptionLink, is(startState.descriptionLink));
      assertThat(returnState.address, is(startState.address));
      assertThat(returnState.primaryMAC, is(startState.primaryMAC));
      assertThat(returnState.powerState, is(startState.powerState));
      assertThat(returnState.adapterManagementReference, is(startState.adapterManagementReference));
    }

    @Test
    public void testMissingId() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = buildValidStartState(cd);
      startState.id = null;

      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test
    public void testMissingDescriptionLink() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = buildValidStartState(cd);
      startState.powerState = ComputeService.PowerState.OFF;
      startState.descriptionLink = null;

      host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingAdapterManagementReference() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      cd.supportedChildren = new ArrayList<>();
      cd.supportedChildren.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.toString());
      ComputeService.ComputeState startState = buildValidStartState(cd);
      startState.adapterManagementReference = null;

      host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class,
          IllegalArgumentException.class);
    }
  }

  /**
   * This class implements tests for the handleGet method.
   */
  public class HandleGetTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testGet() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = buildValidStartState(cd);

      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);
      assertNotNull(returnState);

      ComputeService.ComputeState getState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          ComputeService.ComputeState.class
      );

      assertThat(getState.id, is(startState.id));
      assertThat(getState.descriptionLink, is(startState.descriptionLink));
      assertThat(getState.address, is(startState.address));
      assertThat(getState.primaryMAC, is(startState.primaryMAC));
      assertThat(getState.powerState, is(startState.powerState));
      assertThat(getState.adapterManagementReference, is(startState.adapterManagementReference));
    }

    @Test
    public void testGetExpand() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeStateWithDescription startState = buildValidStartState(cd);

      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);
      assertNotNull(returnState);

      ComputeService.ComputeStateWithDescription getState = host.getServiceSynchronously(
          UriUtils.buildExpandLinksQueryUri(URI.create(returnState.documentSelfLink)).toString(),
          ComputeService.ComputeStateWithDescription.class
      );

      assertThat(getState.id, is(startState.id));
      assertNotNull(getState.description);
      assertThat(getState.description.id, is(startState.description.id));
      assertThat(getState.description.name, is(startState.description.name));
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
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = buildValidStartState(cd);

      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);
      assertNotNull(returnState);

      ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
      patchBody.id = UUID.randomUUID().toString();
      patchBody.address = "10.0.0.2";
      patchBody.powerState = ComputeService.PowerState.OFF;
      patchBody.primaryMAC = "ba:98:76:54:32:10";
      patchBody.resourcePoolLink = "http://newResourcePool";
      patchBody.adapterManagementReference = URI.create("http://newAdapterManagementReference");
      host.patchServiceSynchronously(returnState.documentSelfLink, patchBody);

      ComputeService.ComputeStateWithDescription getState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          ComputeService.ComputeStateWithDescription.class
      );

      assertThat(getState.id, is(patchBody.id));
      assertThat(getState.address, is(patchBody.address));
      assertThat(getState.powerState, is(patchBody.powerState));
      assertThat(getState.primaryMAC, is(patchBody.primaryMAC));
      assertThat(getState.resourcePoolLink, is(patchBody.resourcePoolLink));
      assertThat(getState.adapterManagementReference, is(patchBody.adapterManagementReference));
    }

    @Test
    public void testPatchNoChange() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState startState = buildValidStartState(cd);

      ComputeService.ComputeState returnState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          startState,
          ComputeService.ComputeState.class);
      assertNotNull(returnState);

      ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
      host.patchServiceSynchronously(returnState.documentSelfLink, patchBody);

      ComputeService.ComputeStateWithDescription getState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          ComputeService.ComputeStateWithDescription.class
      );

      assertThat(getState.id, is(startState.id));
      assertThat(getState.address, is(startState.address));
      assertThat(getState.powerState, is(startState.powerState));
      assertThat(getState.primaryMAC, is(startState.primaryMAC));
      assertThat(getState.resourcePoolLink, is(startState.resourcePoolLink));
      assertThat(getState.adapterManagementReference, is(startState.adapterManagementReference));
    }
  }

  /**
   * This class implements tests for query.
   */
  public class QueryTest extends BaseModelTest {
    public static final int SERVICE_COUNT = 10;

    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testTenantLinksQuery() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      ComputeService.ComputeState cs = buildValidStartState(cd);

      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      cs.tenantLinks = new ArrayList<>();
      cs.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));

      ComputeService.ComputeState startState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK, cs, ComputeService.ComputeState.class);

      String kind = Utils.buildKind(ComputeService.ComputeState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, cs.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }

    @Test
    public void testCustomPropertiesQuery() throws Throwable {
      ComputeService.ComputeState[] initialStates = createInstances(SERVICE_COUNT);

      // Patch only one out of SERVICE_COUNT compute states with custom property:
      String customPropComputeStateLink = initialStates[0].documentSelfLink;
      String newCustomPropertyValue = UUID.randomUUID().toString();

      ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
      patchBody.customProperties = new HashMap<>();
      patchBody.customProperties.put(TEST_DESC_PROPERTY_NAME, newCustomPropertyValue);
      host.patchServiceSynchronously(customPropComputeStateLink, patchBody);

      String kind = Utils.buildKind(ComputeService.ComputeState.class);
      String propertyName = QueryTask.QuerySpecification.buildCompositeFieldName(
          ComputeService.ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, TEST_DESC_PROPERTY_NAME);

      // Query computes with newCustomPropClause and expect 1 instance
      QueryTask q = host.createDirectQueryTask(kind, propertyName, newCustomPropertyValue);
      queryComputes(q, 1);

      // Query computes with old CustomPropClause and expect SERVICE_COUNT-1 instances
      q = host.createDirectQueryTask(kind, propertyName, TEST_DESC_PROPERTY_VALUE);
      queryComputes(q, SERVICE_COUNT - 1);
    }

    private void queryComputes(QueryTask q, int expectedCount) throws Throwable {
      QueryTask queryTask = host.querySynchronously(q);
      assertNotNull(queryTask.results.documentLinks);
      assertFalse(queryTask.results.documentLinks.isEmpty());
      assertThat(queryTask.results.documentLinks.size(), is(expectedCount));
    }

    public ComputeService.ComputeState[] createInstances(int c) throws Throwable {
      ComputeService.ComputeState[] instances = new ComputeService.ComputeState[c];
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(host);
      for (int i = 0; i < c; i++) {
        instances[i] = host.postServiceSynchronously(
            ComputeFactoryService.SELF_LINK,
            buildValidStartState(cd),
            ComputeService.ComputeState.class);
      }
      return instances;
    }
  }
}
