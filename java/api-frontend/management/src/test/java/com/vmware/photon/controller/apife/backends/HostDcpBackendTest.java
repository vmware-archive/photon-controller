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
import com.vmware.photon.controller.api.HostDatastore;
import com.vmware.photon.controller.api.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.builders.StatsInfoBuilder;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostAvailabilityZoneAlreadySetException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAvailabilityZoneStateException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link HostDcpBackend}.
 */
public class HostDcpBackendTest {

  private static ApiFeXenonRestClient dcpClient;
  private static BasicServiceHost host;

  @Test
  private void dummy() {
  }

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeXenonRestClient;

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
    deploymentCreateSpec.setStats(new StatsInfoBuilder()
        .enabled(true)
        .storeEndpoint("10.146.64.111")
        .storePort(2004).enabled(true)
        .build());
    deploymentCreateSpec.setUseImageDatastoreForVms(true);
    deploymentCreateSpec.setAuth(new AuthConfigurationSpecBuilder()
        .enabled(true)
        .tenant("t")
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
    private ApiFeXenonRestClient apiFeXenonRestClient;

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
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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

    private static final String HOST_ADDRESS = "0.0.0.1";

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      DeploymentCreateSpec deploymentCreateSpec = getDeploymentCreateSpec();
      TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
      deploymentId = task.getEntityId();

      // create 1st host
      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setMetadata(ImmutableMap.of("k1", "v1"));
      hostCreateSpec.setUsageTags(ImmutableList.of(UsageTag.CLOUD));

      TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
      hostId = taskEntity.getEntityId();

      // create 2nd host
      HostCreateSpec host2CreateSpec = new HostCreateSpec();
      host2CreateSpec.setAddress(HOST_ADDRESS);
      host2CreateSpec.setUsername("user");
      host2CreateSpec.setPassword("password");
      host2CreateSpec.setUsageTags(ImmutableList.of(UsageTag.CLOUD, UsageTag.IMAGE));
      hostBackend.prepareHostCreate(host2CreateSpec, deploymentId);
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
    public void testToApiRepresentationWithOutDatastoreInfo() throws Throwable {
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
    public void testToApiRepresentationWithDatastoreInfo() throws Throwable {
      HostDatastore ds1 = new HostDatastore("id1", "ds1", true);
      HostDatastore ds2 = new HostDatastore("id2", "ds2", false);

      HostService.State updateState = new HostService.State();
      updateState.datastoreServiceLinks =
          new HashMap<>(
              ImmutableMap.of(
                  ds1.getMountPoint(), "/datastores/" + ds1.getDatastoreId(),
                  ds2.getMountPoint(), "/datastores/" + ds2.getDatastoreId()));
      updateState.reportedImageDatastores = new HashSet<>(ImmutableSet.of(ds1.getDatastoreId()));
      dcpClient.patch(HostServiceFactory.SELF_LINK + "/" + hostId, updateState);

      Host host = hostBackend.toApiRepresentation(hostId);
      assertThat(host, notNullValue());
      assertThat(host.getState(), is(HostState.CREATING));
      assertThat(host.getUsername(), is(hostCreateSpec.getUsername()));
      assertThat(host.getPassword(), is(hostCreateSpec.getPassword()));
      assertThat(host.getAddress(), is(hostCreateSpec.getAddress()));
      assertThat(host.getAvailabilityZone(), is(hostCreateSpec.getAvailabilityZone()));
      assertThat(host.getMetadata().get("k1"), is("v1"));
      assertThat(host.getUsageTags().get(0), is(UsageTag.CLOUD));
      assertThat(host.getDatastores(), containsInAnyOrder(ds1, ds2));
    }

    @Test
    public void testFilterByUsage() throws Throwable {
      ResourceList<Host> hosts = hostBackend.filterByUsage(UsageTag.CLOUD, Optional.of(PaginationConfig
          .DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(hosts.getItems().size(), is(2));

      hosts = hostBackend.filterByUsage(UsageTag.MGMT, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(hosts.getItems().size(), is(0));

      hosts = hostBackend.filterByUsage(UsageTag.IMAGE, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(hosts.getItems().size(), is(1));
    }

    @Test
    public void testListAll() {
      ResourceList<Host> hosts = hostBackend.listAll(Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(hosts, notNullValue());
      assertThat(hosts.getItems().size(), is(2));
    }

    @Test
    public void testFilterByIp() {
      ResourceList<Host> hosts = hostBackend.filterByAddress(HOST_ADDRESS, Optional.absent());
      assertThat(hosts, notNullValue());
      assertThat(hosts.getItems().size(), is(1));
      assertThat(hosts.getItems().get(0).getAddress(), is(HOST_ADDRESS));

      hosts = hostBackend.filterByAddress("192.168.1.1", Optional.absent());
      assertThat(hosts, notNullValue());
      assertThat(hosts.getItems().size(), is(0));
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
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.SUSPEND_HOST));

      hostBackend.updateState(hostEntity, HostState.SUSPENDED);
      taskEntity = hostBackend.resume(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.RESUME_HOST));

    }

    @Test
    public void tesEnterAndExitMaintenance() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      hostBackend.updateState(hostEntity, HostState.SUSPENDED);
      TaskEntity taskEntity = hostBackend.enterMaintenance(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(2));
      assertThat(taskEntity.getSteps().get(0).getOperation(), is(Operation.ENTER_MAINTENANCE_MODE));

      hostBackend.updateState(hostEntity, HostState.MAINTENANCE);
      taskEntity = hostBackend.exitMaintenance(hostId);

      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getSteps().size(), is(2));
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
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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

  /**
   * Tests for updating Hosts.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class SetHostAvailabilityZoneTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private AvailabilityZoneBackend availabilityZoneBackend;

    private HostCreateSpec hostCreateSpec;
    private AvailabilityZoneCreateSpec availabilityZoneCreateSpec;
    private HostSetAvailabilityZoneOperation hostSetAvailabilityZoneOperation;

    private String hostId;
    private String deploymentId;
    private String availabilityZoneId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

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

      availabilityZoneCreateSpec = new AvailabilityZoneCreateSpec();
      availabilityZoneCreateSpec.setName("availability-zone");
      taskEntity = availabilityZoneBackend.createAvailabilityZone(availabilityZoneCreateSpec);
      availabilityZoneId = taskEntity.getEntityId();

      hostSetAvailabilityZoneOperation = new HostSetAvailabilityZoneOperation();
      hostSetAvailabilityZoneOperation.setAvailabilityZoneId(availabilityZoneId);
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
    public void testSetAvailabilityZone() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      assertThat(hostEntity.getAvailabilityZone(), is(nullValue()));

      TaskEntity taskEntity = hostBackend.setAvailabilityZone(hostId, hostSetAvailabilityZoneOperation);
      String hostId = taskEntity.getEntityId();
      assertThat(hostId, notNullValue());
      assertThat(taskEntity.getEntityId(), is(hostId));
      assertThat(taskEntity.getState(), is(TaskEntity.State.QUEUED));
      assertThat(taskEntity.getOperation(), is(Operation.SET_AVAILABILITYZONE));

      hostEntity.setAvailabilityZone(availabilityZoneId);
      hostBackend.updateAvailabilityZone(hostEntity);

      Host host = hostBackend.toApiRepresentation(hostId);
      assertThat(host, notNullValue());
      assertThat(host.getAvailabilityZone(), is(availabilityZoneId));
    }

    @Test
    public void testSetAvailabilityZoneWhenAvailabilityZoneAlreadySet() throws Throwable {
      HostEntity hostEntity = hostBackend.findById(hostId);
      assertThat(hostEntity.getAvailabilityZone(), is(nullValue()));

      hostBackend.setAvailabilityZone(hostId, hostSetAvailabilityZoneOperation);

      hostEntity.setAvailabilityZone(availabilityZoneId);
      hostBackend.updateAvailabilityZone(hostEntity);

      Host host = hostBackend.toApiRepresentation(hostId);
      assertThat(host, notNullValue());
      assertThat(host.getAvailabilityZone(), is(availabilityZoneId));

      try {
        hostBackend.setAvailabilityZone(hostId, hostSetAvailabilityZoneOperation);
        fail("should have failed with HostAvailabilityZoneAlreadySetException");
      } catch (HostAvailabilityZoneAlreadySetException e) {
        assertThat(e.getErrorCode(), is (ErrorCode.HOST_AVAILABILITYZONE_ALREADY_SET.getCode()));
        assertThat(e.getMessage(),
                   is("Host " + hostId + " is already part of Availability Zone " + availabilityZoneId));
      }
    }
  }
}
