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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * This class implements tests for the {@link ResourceDescriptionService} class.
 */
public class SnapshotServiceTest {

  private SnapshotService.SnapshotState buildValidStartState() throws Throwable {
    SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
    st.id = UUID.randomUUID().toString();
    st.name = "friendly-name";
    st.computeLink = "compute-link";
    st.description = "description";
    st.customProperties = new HashMap<>();
    st.customProperties.put("defaultKey", "defaultVal");

    return st;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private SnapshotService snapshotService;

    @BeforeMethod
    public void setUpTest() {
      snapshotService = new SnapshotService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);

      assertThat(snapshotService.getOptions(), is(expected));
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
      SnapshotService.SnapshotState startState = buildValidStartState();
      assertNotNull(host);

      SnapshotService.SnapshotState returnState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.computeLink, is(startState.computeLink));
    }

    @Test
    public void testMissingId() throws Throwable {

      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.id = null;

      SnapshotService.SnapshotState returnState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test
    public void testMissingName() throws Throwable {
      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.name = null;

      host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingComputeLink() throws Throwable {
      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.computeLink = null;

      host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class,
          IllegalArgumentException.class);
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
    public void testPatchSnapshotName() throws Throwable {
      SnapshotService.SnapshotState startState = createSnapshotService();

      SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
      patchState.name = UUID.randomUUID().toString();
      host.patchServiceSynchronously(startState.documentSelfLink, patchState);

      SnapshotService.SnapshotState  newState = host.getServiceSynchronously(
          startState.documentSelfLink, SnapshotService.SnapshotState.class);
      assertThat(newState.name, is(patchState.name));
    }

    @Test
    public void testPatchSnapshotDescription() throws Throwable {
      SnapshotService.SnapshotState startState = createSnapshotService();

      SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
      patchState.description = "test-description";
      host.patchServiceSynchronously(startState.documentSelfLink, patchState);

      SnapshotService.SnapshotState  newState = host.getServiceSynchronously(
          startState.documentSelfLink, SnapshotService.SnapshotState.class);

      assertThat(newState.description, is(patchState.description));
    }

    @Test
    public void testPatchSnapshotComputeLink() throws Throwable {
      SnapshotService.SnapshotState startState = createSnapshotService();

      SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
      patchState.computeLink = "test-compute-link";
      host.patchServiceSynchronously(startState.documentSelfLink, patchState);

      SnapshotService.SnapshotState  newState = host.getServiceSynchronously(
          startState.documentSelfLink, SnapshotService.SnapshotState.class);

      assertThat(newState.computeLink, is(patchState.computeLink));
    }

    @Test
    public void testPatchSnapshotCustomProperties() throws Throwable {
      SnapshotService.SnapshotState startState = createSnapshotService();

      SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
      patchState.customProperties = new HashMap<>();
      patchState.customProperties.put("key1", "val1");
      patchState.customProperties.put("key2", "val2");
      patchState.customProperties.put("key3", "val3");

      host.patchServiceSynchronously(startState.documentSelfLink, patchState);

      SnapshotService.SnapshotState  newState = host.getServiceSynchronously(
          startState.documentSelfLink, SnapshotService.SnapshotState.class);


      for (Map.Entry<String, String> entry: patchState.customProperties.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        String newStateProperty = newState.customProperties.get(key);
        assertNotNull(newStateProperty);
        assert(newStateProperty.equals(value));
      }

    }

    private SnapshotService.SnapshotState createSnapshotService() throws Throwable {
      SnapshotService.SnapshotState startState = buildValidStartState();
      return host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK, startState, SnapshotService.SnapshotState.class);
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
      SnapshotService.SnapshotState st = buildValidStartState();

      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      st.tenantLinks = new ArrayList<>();
      st.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));

      SnapshotService.SnapshotState startState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK, st, SnapshotService.SnapshotState.class);

      String kind = Utils.buildKind(SnapshotService.SnapshotState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, st.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }
}
