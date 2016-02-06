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

/**
 * This class implements tests for the {@link ResourceDescriptionService} class.
 */
public class ResourceDescriptionServiceTest {

  private ResourceDescriptionService.ResourceDescription buildValidStartState() throws Throwable {
    ResourceDescriptionService.ResourceDescription rd = new ResourceDescriptionService.ResourceDescription();

    rd.computeType = "compute-type";
    rd.computeDescriptionLink = "compute-description-link";

    return rd;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ResourceDescriptionService resourceDescriptionService;

    @BeforeMethod
    public void setUpTest() {
      resourceDescriptionService = new ResourceDescriptionService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(resourceDescriptionService.getOptions(), is(expected));
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
      ResourceDescriptionService.ResourceDescription startState = buildValidStartState();
      ResourceDescriptionService.ResourceDescription returnState = host.postServiceSynchronously(
          ResourceDescriptionFactoryService.SELF_LINK,
          startState,
          ResourceDescriptionService.ResourceDescription.class);

      assertNotNull(returnState);
      assertThat(returnState.computeType, is(startState.computeType));
      assertThat(returnState.computeDescriptionLink, is(startState.computeDescriptionLink));
    }

    @Test
    public void testMissingComputeType() throws Throwable {
      ResourceDescriptionService.ResourceDescription startState = buildValidStartState();
      startState.computeType = null;

      host.postServiceSynchronously(
          ResourceDescriptionFactoryService.SELF_LINK,
          startState,
          ResourceDescriptionService.ResourceDescription.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingComputeDescriptionLink() throws Throwable {
      ResourceDescriptionService.ResourceDescription startState = buildValidStartState();
      startState.computeDescriptionLink = null;

      host.postServiceSynchronously(
          ResourceDescriptionFactoryService.SELF_LINK,
          startState,
          ResourceDescriptionService.ResourceDescription.class,
          IllegalArgumentException.class);
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
      ResourceDescriptionService.ResourceDescription rd = buildValidStartState();

      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      rd.tenantLinks = new ArrayList<>();
      rd.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));

      ResourceDescriptionService.ResourceDescription startState = host.postServiceSynchronously(
          ResourceDescriptionFactoryService.SELF_LINK, rd, ResourceDescriptionService.ResourceDescription.class);

      String kind = Utils.buildKind(ResourceDescriptionService.ResourceDescription.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, rd.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }
}
