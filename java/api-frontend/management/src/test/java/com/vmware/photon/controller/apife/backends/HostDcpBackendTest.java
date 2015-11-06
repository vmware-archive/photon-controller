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

import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.powermock.core.classloader.annotations.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests {@link HostDcpBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class HostDcpBackendTest {

  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;
  private static HostBackend hostBackend;

  @Test
  private void dummy() {
  }

  private static void commonSetup(TaskBackend taskBackend, EntityLockBackend entityLockBackend,
                                  DeploymentBackend deploymentBackend, TombstoneBackend tombstoneBackend)
      throws Throwable {
    host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
        BasicServiceHost.BIND_PORT,
        null,
        HostServiceFactory.SELF_LINK,
        10, 10);

    host.startServiceSynchronously(new HostServiceFactory(), null);

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));
    hostBackend = new HostDcpBackend(dcpClient, taskBackend, entityLockBackend, deploymentBackend, tombstoneBackend);
  }

  private static void commonTearDown() throws Throwable {
    if (host != null) {
      BasicServiceHost.destroy(host);
    }

    dcpClient.stop();
  }

  private static DeploymentCreateSpec getDeploymentCreateSpec() {
    DeploymentCreateSpec deploymentCreateSpec = new DeploymentCreateSpec();
    deploymentCreateSpec.setImageDatastore("imageDatastore");
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

  /**
   * Tests for creating Hosts.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateHostTest extends BaseDaoTest {

    @Inject
    private EntityLockBackend entityLockBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private HostCreateSpec hostCreateSpec;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup(
          taskBackend,
          entityLockBackend,
          deploymentBackend,
          tombstoneBackend);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setAvailabilityZone("availabilityZone");
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
      super.tearDown();
      commonTearDown();
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
  }

  /**
   * Tests for querying Hosts.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class QueryHostTest extends BaseDaoTest {

    @Mock
    private EntityLockBackend entityLockBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup(
          taskBackend,
          entityLockBackend,
          deploymentBackend,
          tombstoneBackend);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setAvailabilityZone("availabilityZone");
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
      super.tearDown();
      commonTearDown();
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
      List<Task> taskList = hostBackend.getTasks(hostId, Optional.<String>absent());
      assertThat(taskList.size(), is(1));

      hostBackend.updateState(hostBackend.findById(hostId), HostState.READY);
      hostBackend.suspend(hostId);
      taskList = hostBackend.getTasks(hostId, Optional.<String>absent());
      assertThat(taskList.size(), is(2));

      taskList = hostBackend.getTasks(hostId, Optional.of(TaskEntity.State.QUEUED.name()));
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
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class UpdateHostTest extends BaseDaoTest {

    @Mock
    private EntityLockBackend entityLockBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup(
          taskBackend,
          entityLockBackend,
          deploymentBackend,
          tombstoneBackend);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setAvailabilityZone("availabilityZone");
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
      super.tearDown();
      commonTearDown();
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
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class DeleteHostTest extends BaseDaoTest {

    @Mock
    private EntityLockBackend entityLockBackend;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private HostCreateSpec hostCreateSpec;

    private String hostId;
    private String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();
      commonSetup(
          taskBackend,
          entityLockBackend,
          deploymentBackend,
          tombstoneBackend);

      hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setUsername("user");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("0.0.0.0");
      hostCreateSpec.setAvailabilityZone("availabilityZone");
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
      super.tearDown();
      commonTearDown();
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
