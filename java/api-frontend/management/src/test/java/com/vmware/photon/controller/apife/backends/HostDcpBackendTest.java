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

import com.vmware.photon.controller.api.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.AvailabilityZoneState;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAvailabilityZoneStateException;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link HostDcpBackend}.
 */
public class HostDcpBackendTest {

  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  @Test
  private void dummy() {
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

  private static DeploymentCreateSpec getDeploymentCreateSpec() {
    DeploymentCreateSpec deploymentCreateSpec = new DeploymentCreateSpec();
    deploymentCreateSpec.setImageDatastores(Collections.singleton("imageDatastore"));
    deploymentCreateSpec.setNtpEndpoint("ntp");
    deploymentCreateSpec.setSyslogEndpoint("syslog");
    deploymentCreateSpec.setUseImageDatastoreForVms(true);
    deploymentCreateSpec.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("https://10.146.39.198:7444/lookupservice/sdk")
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(Arrays.asList(new String[]{"securityGroup1", "securityGroup2"}))
        .build());
    return deploymentCreateSpec;
  }

  private static AvailabilityZoneCreateSpec getAvailabilityZoneCreateSpec() {
    AvailabilityZoneCreateSpec availabilityZoneCreateSpec = new AvailabilityZoneCreateSpec();
    availabilityZoneCreateSpec.setName(UUID.randomUUID().toString());
    return availabilityZoneCreateSpec;
  }

  /**
   * Tests for creating Hosts.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class CreateHostTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private AvailabilityZoneBackend availabilityZoneBackend;

    private HostCreateSpec hostCreateSpec;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setMetadata(new HashMap<String, String>() {{
        put("k1", "v1");
      }});
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      hostCreateSpec.setUsageTags(usageTags);

      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    private AvailabilityZoneEntity createAvailabilityZoneDocument() throws ExternalException {
      AvailabilityZoneCreateSpec availabilityZoneCreateSpec = getAvailabilityZoneCreateSpec();
      TaskEntity task = availabilityZoneBackend.createAvailabilityZone(availabilityZoneCreateSpec);
      AvailabilityZoneEntity availabilityZoneEntity = availabilityZoneBackend.getEntityById(task.getEntityId());
      assertThat(availabilityZoneEntity.getState(), is(AvailabilityZoneState.READY));
      return availabilityZoneEntity;
    }

    private AvailabilityZoneEntity deleteAvailabilityZoneDocument(String availabilityZoneId) throws ExternalException {
      TaskEntity task = availabilityZoneBackend.prepareAvailabilityZoneDelete(availabilityZoneId);
      AvailabilityZoneEntity availabilityZoneEntity = availabilityZoneBackend.getEntityById(task.getEntityId());
      assertThat(availabilityZoneEntity.getState(), is(AvailabilityZoneState.PENDING_DELETE));
      return availabilityZoneEntity;
    }

    @Test
    public void testPrepareHostCreate() throws Throwable {
      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);

      String hostId = taskEntity.getEntityId();
      assertThat(hostId, notNullValue());
      HostEntity hostEntity = hostBackend.findById(hostId);
      assertThat(hostEntity, notNullValue());
      assertThat(hostEntity.getState(), is(HostState.CREATING));
      assertThat(hostEntity.getUsername(), is(hostCreateSpec.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostCreateSpec.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostCreateSpec.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostCreateSpec.getAvailabilityZone()));
      assertThat(hostEntity.getMetadata().get("k1"), is("v1"));
      assertThat(hostEntity.getUsageTags(), containsString("CLOUD"));
    }

    @Test
    public void testPrepareHostCreateWithAvailabilityZone() throws Throwable {
      AvailabilityZoneEntity availabilityZoneEntity = createAvailabilityZoneDocument();
      hostCreateSpec.setAvailabilityZone(availabilityZoneEntity.getId());

      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);

      String hostId = taskEntity.getEntityId();
      assertThat(hostId, notNullValue());
      HostEntity hostEntity = hostBackend.findById(hostId);
      assertThat(hostEntity, notNullValue());
      assertThat(hostEntity.getState(), is(HostState.CREATING));
      assertThat(hostEntity.getUsername(), is(hostCreateSpec.getUsername()));
      assertThat(hostEntity.getPassword(), is(hostCreateSpec.getPassword()));
      assertThat(hostEntity.getAddress(), is(hostCreateSpec.getAddress()));
      assertThat(hostEntity.getAvailabilityZone(), is(hostCreateSpec.getAvailabilityZone()));
      assertThat(hostEntity.getMetadata().get("k1"), is("v1"));
      assertThat(hostEntity.getUsageTags(), containsString("CLOUD"));
    }

    @Test
    public void testPrepareHostCreateWithNonExistingAvailabilityZone() throws Throwable {
      String availabilityZoneId = UUID.randomUUID().toString();
      hostCreateSpec.setAvailabilityZone(availabilityZoneId);

      try {
        hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
        fail("should have failed with AvailabilityZoneNotFoundException");
      } catch (AvailabilityZoneNotFoundException e) {
        assertThat(e.getMessage(), is("AvailabilityZone " + availabilityZoneId + " not found"));
      }
    }

    @Test
    public void testPrepareHostCreateWithInvalidAvailabilityZoneState() throws Throwable {
      AvailabilityZoneEntity availabilityZoneEntity = createAvailabilityZoneDocument();
      availabilityZoneEntity = deleteAvailabilityZoneDocument(availabilityZoneEntity.getId());
      hostCreateSpec.setAvailabilityZone(availabilityZoneEntity.getId());

      try {
        hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
        fail("should have failed with InvalidAvailabilityZoneStateException");
      } catch (InvalidAvailabilityZoneStateException e) {
        assertThat(e.getMessage(),
            is("AvailabilityZone " + availabilityZoneEntity.getId() + " is in PENDING_DELETE state"));
      }
    }
  }

  /**
   * Tests for querying Hosts.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class QueryHostTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setMetadata(new HashMap<String, String>() {{
        put("k1", "v1");
      }});
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      hostCreateSpec.setUsageTags(usageTags);

      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();

      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      hostId = taskEntity.getEntityId();
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
    public void testToApiRepresentation() throws Throwable {
      Host host = hostBackend.toApiRepresentation(hostId);
      assertThat(host, notNullValue());
      assertThat(host.getState(), is(HostState.CREATING));
      assertThat(host.getUsername(), is(hostCreateSpec.getUsername()));
      assertThat(host.getPassword(), is(hostCreateSpec.getPassword()));
      assertThat(host.getAddress(), is(hostCreateSpec.getAddress()));
      assertThat(host.getAvailabilityZone(), is(hostCreateSpec.getAvailabilityZone()));
      assertThat(host.getMetadata().get("k1"), is("v1"));
      assertThat(host.getUsageTags().get(0), is(UsageTag.CLOUD));
    }

    @Test
    public void testFilterHost() throws Throwable {
      hostCreateSpec.setAddress("0.0.0.1");
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      usageTags.add(UsageTag.IMAGE);
      hostCreateSpec.setUsageTags(usageTags);
      hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);

      List<Host> hosts = hostBackend.listAll();
      assertThat(hosts, notNullValue());
      assertThat(hosts.size(), is(2));

      hosts = hostBackend.filterByUsage(UsageTag.CLOUD);
      assertThat(hosts.size(), is(2));

      hosts = hostBackend.filterByUsage(UsageTag.IMAGE);
      assertThat(hosts.size(), is(1));

      hosts = hostBackend.filterByUsage(UsageTag.MGMT);
      assertThat(hosts.size(), is(0));
    }

    @Test
    public void testGetTasks() throws Throwable {
      List<Task> taskList = hostBackend.getTasks(hostId, Optional.<String>absent(), Optional.<Integer>absent());
      assertThat(taskList.size(), is(1));

      hostBackend.updateState(hostBackend.findById(hostId), HostState.READY);
      hostBackend.suspend(hostId);
      taskList = hostBackend.getTasks(hostId, Optional.<String>absent(), Optional.<Integer>absent());
      assertThat(taskList.size(), is(2));

      taskList = hostBackend.getTasks(hostId, Optional.of(TaskEntity.State.QUEUED.name()), Optional.<Integer>absent());
      assertThat(taskList.size(), is(2));
    }

    @Test
    public void testGetInvalidHost() throws Throwable {
      try {
        hostBackend.toApiRepresentation("invalid-host");
        fail("should have failed with HostNotFoundException");
      } catch (HostNotFoundException e) {
        assertThat(e.getMessage(), is("Host #invalid-host not found"));
      }
    }
  }

  /**
   * Tests for updating Hosts.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class UpdateHostTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setMetadata(new HashMap<String, String>() {{
        put("k1", "v1");
      }});
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      hostCreateSpec.setUsageTags(usageTags);

      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();

      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);

      hostId = taskEntity.getEntityId();
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
    public void tesUpdateState() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.ERROR);

      Host host = hostBackend.toApiRepresentation(hostId);
      assertThat(host, notNullValue());
      assertThat(host.getState(), is(HostState.ERROR));
    }

    @Test
    public void tesSuspendAndResume() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.READY);
      TaskEntity taskEntity = hostBackend.suspend(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SUSPEND_HOST));

      hostBackend.updateState(hostEntity, HostState.SUSPENDED);
      taskEntity = hostBackend.resume(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.RESUME_HOST));

    }

    @Test
    public void tesEnterAndExitMaintenance() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.SUSPENDED);
      TaskEntity taskEntity = hostBackend.enterMaintenance(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.ENTER_MAINTENANCE_MODE));

      hostBackend.updateState(hostEntity, HostState.MAINTENANCE);
      taskEntity = hostBackend.exitMaintenance(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.EXIT_MAINTENANCE_MODE));
    }

    @Test
    public void tesUpdateInvalidHost() throws Throwable {
      HostEntity hostEntity = new HostEntity();
      hostEntity.setId("invalid-host");
      try {
        hostBackend.updateState(hostEntity, HostState.ERROR);
        fail("should have failed with HostNotFoundException");
      } catch (HostNotFoundException e) {
        assertThat(e.getMessage(), is("Host #invalid-host not found"));
      }
    }

  }

  /**
   * Tests for deleting and tombstoning Hosts.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class DeleteHostTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setMetadata(new HashMap<String, String>() {{
        put("k1", "v1");
      }});
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      hostCreateSpec.setUsageTags(usageTags);

      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();

      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);

      hostId = taskEntity.getEntityId();
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
    public void testPrepareHostDelete() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.NOT_PROVISIONED);
      TaskEntity taskEntity = hostBackend.prepareHostDelete(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getOperation(), is(Operation.DELETE_HOST));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.DELETE_HOST));
    }

    @Test
    public void testPrepareHostErrorStateDelete() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      TaskEntity taskEntity = hostBackend.prepareHostDelete(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getOperation(), is(Operation.DELETE_HOST));
      assertThat(taskEntity.getSteps().size(), is(1));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.DELETE_HOST));
    }

    @Test
    public void testDeleteInvalidState() throws Throwable {
      try {
        HostEntity hostEntity = hostBackend.findById(hostId);
        hostBackend.updateState(hostEntity, HostState.READY);
        hostBackend.prepareHostDelete(hostId);
        fail("should have failed with InvalidOperationStateException");
      } catch (InvalidOperationStateException e) {
        assertThat(e.getMessage(), containsString("Invalid operation DELETE_HOST"));
      }
    }

    @Test
    public void testDeleteInvalidHost() throws Throwable {
      try {
        hostBackend.prepareHostDelete("invalid-host");
        fail("should have failed with HostNotFoundException");
      } catch (HostNotFoundException e) {
        assertThat(e.getMessage(), is("Host #invalid-host not found"));
      }
    }

    @Test
    public void testTombstoneHost() throws Throwable {
      hostBackend.tombstone(hostBackend.findById(hostId));

      try {
        hostBackend.findById(hostId);
        fail("should have failed with HostNotFoundException");
      } catch (HostNotFoundException e) {
        assertThat(e.getMessage(), is("Host #" + hostId + " not found"));
      }
    }
  }
}
