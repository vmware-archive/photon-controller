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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.model.AvailabilityZoneState;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Operation;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.testng.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.backends.AvailabilityZoneXenonBackend}.
 */
public class AvailabilityZoneXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;
  private static TaskEntity createdAvailabilityZoneTaskEntity;

  private static AvailabilityZoneService.State createTestAvailabilityZone(AvailabilityZoneState availabilityZoneState) {
    AvailabilityZoneService.State availabilityZone = new AvailabilityZoneService.State();
    availabilityZone.name = UUID.randomUUID().toString();
    availabilityZone.state = availabilityZoneState;
    return availabilityZone;
  }

  private static String createTestAvailabilityZoneDocument(AvailabilityZoneService.State availabilityZone) {
    Operation result = xenonClient.post(AvailabilityZoneServiceFactory.SELF_LINK, availabilityZone);
    AvailabilityZoneService.State createdAvailabilityZone = result.getBody(AvailabilityZoneService.State.class);
    return ServiceUtils.getIDFromDocumentSelfLink(createdAvailabilityZone.documentSelfLink);
  }

  private static AvailabilityZoneCreateSpec createTestAvailabilityZoneSpec() {
    AvailabilityZoneCreateSpec availabilityZoneCreateSpec = new AvailabilityZoneCreateSpec();
    availabilityZoneCreateSpec.setName(UUID.randomUUID().toString());
    return availabilityZoneCreateSpec;
  }

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for creating AvailabilityZone.
   */
  @Guice(modules = XenonBackendTestModule.class)
  public static class CreateAvailabilityZoneTest {

    private AvailabilityZoneCreateSpec availabilityZoneCreateSpec;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private AvailabilityZoneXenonBackend availabilityZoneXenonBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      availabilityZoneCreateSpec = createTestAvailabilityZoneSpec();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testCreateAvailabilityZone() throws Throwable {
      createdAvailabilityZoneTaskEntity = availabilityZoneXenonBackend.createAvailabilityZone(
          availabilityZoneCreateSpec);

      String availabilityZoneId = createdAvailabilityZoneTaskEntity.getEntityId();
      AvailabilityZoneEntity availabilityZone = availabilityZoneXenonBackend.getEntityById(availabilityZoneId);
      assertThat(availabilityZone, is(notNullValue()));
      assertThat(availabilityZone.getName(), is(availabilityZoneCreateSpec.getName()));
      assertThat(availabilityZone.getKind(), is("availability-zone"));
      assertThat(availabilityZone.getState(), is(AvailabilityZoneState.READY));
      assertThat(availabilityZone.getId(), is(availabilityZoneId));
    }
  }

  /**
   * Tests for getting AvailabilityZone.
   */
  @Guice(modules = XenonBackendTestModule.class)
  public static class GetAvailabilityZoneTest {

    private AvailabilityZoneService.State availabilityZone;
    private String availabilityZoneId;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private AvailabilityZoneXenonBackend availabilityZoneXenonBackend;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      availabilityZone = createTestAvailabilityZone(AvailabilityZoneState.CREATING);
      availabilityZoneId = createTestAvailabilityZoneDocument(availabilityZone);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testGetEntityById() throws Throwable {
      AvailabilityZoneEntity foundAvailabilityZoneEntity = availabilityZoneXenonBackend.getEntityById(
          availabilityZoneId);
      assertThat(foundAvailabilityZoneEntity.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZoneEntity.getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetEntityByIdWithNonExistingId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        availabilityZoneXenonBackend.getEntityById(id);
        fail("availabilityZoneXenonBackend.getEntityById for a non existing id should have failed");
      } catch (AvailabilityZoneNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }

    @Test
    public void testGetApiRepresentation() throws ExternalException {
      AvailabilityZone foundAvailabilityZone = availabilityZoneXenonBackend.getApiRepresentation(availabilityZoneId);
      assertThat(foundAvailabilityZone.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZone.getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetAll() throws Throwable {
      createTestAvailabilityZoneDocument(createTestAvailabilityZone(AvailabilityZoneState.CREATING));
      ResourceList<AvailabilityZoneEntity> foundAvailabilityZoneEntities = availabilityZoneXenonBackend.getAll(
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(foundAvailabilityZoneEntities.getItems().isEmpty(), is(false));
      assertThat(foundAvailabilityZoneEntities.getItems().size(), is(2));
      assertThat(foundAvailabilityZoneEntities.getItems().get(0).getState(), is(availabilityZone.state));
      assertThat(foundAvailabilityZoneEntities.getItems().get(1).getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetListApiRepresentation() throws Throwable {
      createTestAvailabilityZoneDocument(createTestAvailabilityZone(AvailabilityZoneState.CREATING));
      ResourceList<AvailabilityZone> foundAvailabilityZones = availabilityZoneXenonBackend.getListApiRepresentation
          (Optional.of(1));
      assertThat(foundAvailabilityZones.getItems().isEmpty(), is(false));
      assertThat(foundAvailabilityZones.getItems().size(), is(1));
      assertThat(foundAvailabilityZones.getItems().get(0).getState(), is(availabilityZone.state));

      foundAvailabilityZones = availabilityZoneXenonBackend.getPage(foundAvailabilityZones.getNextPageLink());
      assertThat(foundAvailabilityZones.getItems().isEmpty(), is(false));
      assertThat(foundAvailabilityZones.getItems().size(), is(1));
      assertThat(foundAvailabilityZones.getItems().get(0).getState(), is(availabilityZone.state));
    }
  }

  /**
   * Tests for prepareAvailabilityZoneDelete.
   */
  @Guice(modules = XenonBackendTestModule.class)
  public static class PrepareAvailabilityZoneDeleteTest {

    private AvailabilityZoneService.State availabilityZone;
    private String availabilityZoneId;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private AvailabilityZoneXenonBackend availabilityZoneXenonBackend;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      availabilityZone = createTestAvailabilityZone(AvailabilityZoneState.READY);
      availabilityZoneId = createTestAvailabilityZoneDocument(availabilityZone);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareAvailabilityZoneDelete() throws Throwable {
      TaskEntity task = availabilityZoneXenonBackend.prepareAvailabilityZoneDelete(availabilityZoneId);
      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.COMPLETED));

      AvailabilityZoneEntity foundAvailabilityZoneEntity = availabilityZoneXenonBackend.getEntityById(
          availabilityZoneId);
      assertThat(foundAvailabilityZoneEntity.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZoneEntity.getState(), is(AvailabilityZoneState.PENDING_DELETE));
    }

    @Test
    public void testPrepareAvailabilityZoneDeleteWithNonExistingId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        availabilityZoneXenonBackend.prepareAvailabilityZoneDelete(id);
        fail("availabilityZoneXenonBackend.prepareAvailabilityZoneDelete for a non existing id should have failed");
      } catch (AvailabilityZoneNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }
  }
}
