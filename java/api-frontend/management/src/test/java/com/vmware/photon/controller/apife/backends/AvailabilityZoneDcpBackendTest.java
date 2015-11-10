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

import com.vmware.dcp.common.Operation;
import com.vmware.photon.controller.api.AvailabilityZone;
import com.vmware.photon.controller.api.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.AvailabilityZoneState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;

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

import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.backends.AvailabilityZoneDcpBackend}.
 */
public class AvailabilityZoneDcpBackendTest {

  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;
  private static TaskEntity createdAvailabilityZoneTaskEntity;

  private static AvailabilityZoneService.State createTestAvailabilityZone(AvailabilityZoneState availabilityZoneState) {
    AvailabilityZoneService.State availabilityZone = new AvailabilityZoneService.State();
    availabilityZone.name = UUID.randomUUID().toString();
    availabilityZone.state = availabilityZoneState;
    return availabilityZone;
  }

  private static String createTestAvailabilityZoneDocument(AvailabilityZoneService.State availabilityZone) {
    Operation result = dcpClient.postAndWait(AvailabilityZoneServiceFactory.SELF_LINK, availabilityZone);
    AvailabilityZoneService.State createdAvailabilityZone = result.getBody(AvailabilityZoneService.State.class);
    return ServiceUtils.getIDFromDocumentSelfLink(createdAvailabilityZone.documentSelfLink);
  }

  private static AvailabilityZoneCreateSpec createTestAvailabilityZoneSpec() {
    AvailabilityZoneCreateSpec availabilityZoneCreateSpec = new AvailabilityZoneCreateSpec();
    availabilityZoneCreateSpec.setName(UUID.randomUUID().toString());
    return availabilityZoneCreateSpec;
  }

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeDcpRestClient apiFeDcpRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeDcpRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
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
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
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
  @Guice(modules = DcpBackendTestModule.class)
  public static class CreateAvailabilityZoneTest {

    private AvailabilityZoneCreateSpec availabilityZoneCreateSpec;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private AvailabilityZoneDcpBackend availabilityZoneDcpBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
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
      createdAvailabilityZoneTaskEntity = availabilityZoneDcpBackend.createAvailabilityZone(availabilityZoneCreateSpec);

      String availabilityZoneId = createdAvailabilityZoneTaskEntity.getEntityId();
      AvailabilityZoneEntity availabilityZone = availabilityZoneDcpBackend.getEntityById(availabilityZoneId);
      assertThat(availabilityZone, is(notNullValue()));
      assertThat(availabilityZone.getName(), is(availabilityZoneCreateSpec.getName()));
      assertThat(availabilityZone.getKind(), is("availability-zone"));
      assertThat(availabilityZone.getState(), is(AvailabilityZoneState.READY));
      assertThat(availabilityZone.getId(), is(availabilityZoneId));
    }

    @Test
    public void testCreateAvailabilityZoneTwiceWithSameName() throws Throwable {
      createdAvailabilityZoneTaskEntity = availabilityZoneDcpBackend.createAvailabilityZone(availabilityZoneCreateSpec);
      AvailabilityZoneEntity availabilityZone = availabilityZoneDcpBackend
          .getEntityById(createdAvailabilityZoneTaskEntity.getEntityId());

      try {
        availabilityZoneDcpBackend.createAvailabilityZone(availabilityZoneCreateSpec);
        fail("availabilityZoneDcpBackend.createAvailabilityZone for existing availability zone should have failed ");
      } catch (NameTakenException e) {
        assertThat(e.getMessage(), containsString(availabilityZone.getName()));
      }
    }
  }

  /**
   * Tests for getting AvailabilityZone.
   */
  @Guice(modules = DcpBackendTestModule.class)
  public static class GetAvailabilityZoneTest {

    private AvailabilityZoneService.State availabilityZone;
    private String availabilityZoneId;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private AvailabilityZoneDcpBackend availabilityZoneDcpBackend;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
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
      AvailabilityZoneEntity foundAvailabilityZoneEntity = availabilityZoneDcpBackend.getEntityById(availabilityZoneId);
      assertThat(foundAvailabilityZoneEntity.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZoneEntity.getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetEntityByIdWithNonExistingId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        availabilityZoneDcpBackend.getEntityById(id);
        fail("availabilityZoneDcpBackend.getEntityById for a non existing id should have failed");
      } catch (AvailabilityZoneNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }

    @Test
    public void testGetApiRepresentation() throws ExternalException {
      AvailabilityZone foundAvailabilityZone = availabilityZoneDcpBackend.getApiRepresentation(availabilityZoneId);
      assertThat(foundAvailabilityZone.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZone.getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetAll() throws Throwable {
      createTestAvailabilityZoneDocument(createTestAvailabilityZone(AvailabilityZoneState.CREATING));
      List<AvailabilityZoneEntity> foundAvailabilityZoneEntities = availabilityZoneDcpBackend.getAll();
      assertThat(foundAvailabilityZoneEntities.isEmpty(), is(false));
      assertThat(foundAvailabilityZoneEntities.size(), is(2));
      assertThat(foundAvailabilityZoneEntities.get(0).getState(), is(availabilityZone.state));
      assertThat(foundAvailabilityZoneEntities.get(1).getState(), is(availabilityZone.state));
    }

    @Test
    public void testGetListApiRepresentation() throws Throwable {
      createTestAvailabilityZoneDocument(createTestAvailabilityZone(AvailabilityZoneState.CREATING));
      List<AvailabilityZone> foundAvailabilityZones = availabilityZoneDcpBackend.getListApiRepresentation();
      assertThat(foundAvailabilityZones.isEmpty(), is(false));
      assertThat(foundAvailabilityZones.size(), is(2));
      assertThat(foundAvailabilityZones.get(0).getState(), is(availabilityZone.state));
      assertThat(foundAvailabilityZones.get(1).getState(), is(availabilityZone.state));
    }
  }

  /**
   * Tests for prepareAvailabilityZoneDelete.
   */
  @Guice(modules = DcpBackendTestModule.class)
  public static class PrepareAvailabilityZoneDeleteTest {

    private AvailabilityZoneService.State availabilityZone;
    private String availabilityZoneId;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private AvailabilityZoneDcpBackend availabilityZoneDcpBackend;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
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
      TaskEntity task = availabilityZoneDcpBackend.prepareAvailabilityZoneDelete(availabilityZoneId);
      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.COMPLETED));

      AvailabilityZoneEntity foundAvailabilityZoneEntity = availabilityZoneDcpBackend.getEntityById(availabilityZoneId);
      assertThat(foundAvailabilityZoneEntity.getName(), is(availabilityZone.name));
      assertThat(foundAvailabilityZoneEntity.getState(), is(AvailabilityZoneState.PENDING_DELETE));
    }

    @Test
    public void testPrepareAvailabilityZoneDeleteWithNonExistingId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        availabilityZoneDcpBackend.prepareAvailabilityZoneDelete(id);
        fail("availabilityZoneDcpBackend.prepareAvailabilityZoneDelete for a non existing id should have failed");
      } catch (AvailabilityZoneNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }
  }
}
