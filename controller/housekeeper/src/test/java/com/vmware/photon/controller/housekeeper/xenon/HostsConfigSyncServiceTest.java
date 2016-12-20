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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.api.model.AgentState;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.helpers.xenon.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.apache.thrift.async.AsyncMethodCallback;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.housekeeper.xenon.HostsConfigSyncService}.
 */
public class HostsConfigSyncServiceTest {

  private BasicServiceHost host;
  private HostsConfigSyncService service;

  private HostsConfigSyncService.State buildValidStartupState() {
    HostsConfigSyncService.State state = new HostsConfigSyncService.State();
    state.batchSize = 5;
    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostsConfigSyncService();
    }

    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new HostsConfigSyncService();
      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    @Test
    public void testStartState() throws Throwable {
      HostsConfigSyncService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      HostsConfigSyncService.State savedState = host.getServiceState(HostsConfigSyncService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertThat(savedState.batchSize, is(startState.batchSize));
    }

    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      HostsConfigSyncService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      HostsConfigSyncService.State savedState = host.getServiceState(HostsConfigSyncService.State.class);
      if (fieldObj.getType().equals(TaskState.class)) {
        assertThat(Utils.toJson(fieldObj.get(savedState)), is(Utils.toJson(value)));
      } else {
        assertThat(fieldObj.get(savedState), is(value));
      }
    }

    @DataProvider(name = "AutoInitializedFields")
    public Object[][] getAutoInitializedFieldsParams() {
      TaskState state = new TaskState();
      state.stage = TaskState.TaskStage.STARTED;

      return new Object[][]{
          {"taskState", state},
          {"batchSize", 5}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    HostsConfigSyncService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new HostsConfigSyncService();
      serviceState = buildValidStartupState();
      host.startServiceSynchronously(service, serviceState);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    @Test
    public void testInvalidPatch() throws Throwable {
      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody("invalid body");

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private HostsConfigSyncService.State request;
    private List<String> hostSelfLinks = new ArrayList<>();
    HostClient hostClient = mock(HostClient.class);

    @BeforeMethod
    public void setUp() throws Throwable {
      reset(hostClient);
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      doReturn(hostClient).when(hostClientFactory).create();
      GetConfigResponse response = new GetConfigResponse();
      response.setResult(GetConfigResultCode.OK);
      Host.AsyncSSLClient.get_host_config_call hostConfigCall = mock(Host.AsyncSSLClient.get_host_config_call.class);
      doReturn(response).when(hostConfigCall).getResult();
      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncSSLClient.get_host_config_call>) invocation.getArguments()[0])
            .onComplete(hostConfigCall);
        return null;
      }).when(hostClient).getHostConfig(any(AsyncMethodCallback.class));

      machine = new TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();
      machine.startFactoryServiceSynchronously(DeploymentServiceFactory.class, DeploymentServiceFactory.SELF_LINK);
      machine.startFactoryServiceSynchronously(HostServiceFactory.class, HostServiceFactory.SELF_LINK);
      machine.startFactoryServiceSynchronously(DatastoreServiceFactory.class, DatastoreServiceFactory.SELF_LINK);
      // Build input.
      request = buildValidStartupState();
    }

    @AfterTest
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
      hostSelfLinks.clear();
    }

    @Test(dataProvider = "Success")
    public void testSuccess(int totalHosts)
        throws Throwable {

      seedTestEnvironment(machine, totalHosts);

      HostsConfigSyncService.State state = machine.callServiceAndWaitForState(
          HostsConfigSyncService.FACTORY_LINK,
          request,
          HostsConfigSyncService.State.class,
          (HostsConfigSyncService.State s) -> s.taskState.stage == TaskState.TaskStage.FINISHED);

      assertThat(state.taskState.stage, is(TaskState.TaskStage.FINISHED));
      verify(hostClient, times(totalHosts)).getHostConfig(any(AsyncMethodCallback.class));
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {0},
          {1},
          {5},
          {23}
      };
    }

    private void seedTestEnvironment(TestEnvironment env, int totalHosts)
        throws Throwable {

      // create datastore
      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = "datastore1";
      datastore.isImageDatastore = false;
      datastore.name = "datastore";
      datastore.type = "SHARED_VMFS";
      datastore.documentSelfLink = datastore.id;
      env.sendPostAndWaitForReplication(DatastoreServiceFactory.SELF_LINK, datastore);

      for (int i = 0; i < totalHosts; i++) {
        // create host
        HostService.State host = new HostService.State();
        host.reportedDatastores = new HashSet<>();
        host.state = HostState.READY;
        host.agentState = AgentState.ACTIVE;
        host.hostAddress = "hostAddress";
        host.userName = "userName";
        host.password = "password";
        host.usageTags = new HashSet<>();
        host.usageTags.add(UsageTag.CLOUD.name());
        host.reportedDatastores.add(datastore.id);
        // These tests don't depend on scheduling constants, so this will assign
        // one manually instead of using the SchedulingConstantGenerator.
        host.schedulingConstant = 1L;

        Operation operation = env.sendPostAndWaitForReplication(HostServiceFactory.SELF_LINK, host);
        HostService.State createdHost = operation.getBody(HostService.State.class);
        hostSelfLinks.add(createdHost.documentSelfLink);
      }
    }
  }
}
