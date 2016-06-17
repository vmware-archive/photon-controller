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

package com.vmware.photon.controller.cloudstore.xenon.task;

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.xenon.task.DatastoreCleanerService}.
 */
public class DatastoreCleanerServiceTest {

  private BasicServiceHost host;
  private DatastoreCleanerService service;

  private DatastoreCleanerService.State buildValidStartupState() {
    DatastoreCleanerService.State state = new DatastoreCleanerService.State();
    state.batchSize = 5;
    state.intervalBetweenBatchTriggersInSeconds = 1L;
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
      service = new DatastoreCleanerService();
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
      service = new DatastoreCleanerService();
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
      DatastoreCleanerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DatastoreCleanerService.State savedState =
          host.getServiceState(DatastoreCleanerService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertThat(savedState.batchSize, is(startState.batchSize));
      assertThat(savedState.intervalBetweenBatchTriggersInSeconds,
          is(startState.intervalBetweenBatchTriggersInSeconds));
    }

    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      DatastoreCleanerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DatastoreCleanerService.State savedState =
          host.getServiceState(DatastoreCleanerService.State.class);
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
    DatastoreCleanerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new DatastoreCleanerService();
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
    private DatastoreCleanerService.State request;
    private List<String> hostSelfLinks = new ArrayList<>();
    private List<String> datastoreSelfLinks = new ArrayList<>();

    @BeforeMethod
    public void setUp() throws Throwable {
      // Build input.
      request = buildValidStartupState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
    }

    private void freeTestEnvironment(TestEnvironment machine) throws Throwable {
      try {
        for (String selfLink : hostSelfLinks) {
          machine.deleteService(selfLink);
        }
        for (String datastoreSelfLink : datastoreSelfLinks) {
          machine.deleteService(datastoreSelfLink);
        }
      } finally {
        hostSelfLinks.clear();
        datastoreSelfLinks.clear();
      }
    }

    @Test(dataProvider = "Success")
    public void testSuccess(
        int totalHosts,
        int hostsWithDatastore,
        int hostsInActiveState,
        int hostsInReadyState)
        throws Throwable {

      machine = TestEnvironment.create(3);
      seedTestEnvironment(
          machine,
          totalHosts,
          hostsWithDatastore,
          hostsInActiveState,
          hostsInReadyState);

      DatastoreCleanerService.State response = machine.callServiceAndWaitForState(
          DatastoreCleanerFactoryService.SELF_LINK,
          request,
          DatastoreCleanerService.State.class,
          (DatastoreCleanerService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      // Wait for the datastore delete operations to be complete
      Thread.sleep(2000);
      assertThat(response.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(getTotalDatastoreCount(machine), is(new Long(hostsWithDatastore)));

      freeTestEnvironment(machine);
    }

    @DataProvider(name = "Success")
    public Object[][] getSuccessData() {
      return new Object[][]{
          {10, 3, 5, 7},
          {10, 0, 10, 10},
          {10, 10, 10, 10}
      };
    }

    private void seedTestEnvironment(TestEnvironment env,
                                     int totalHosts,
                                     int hostsWithDatastore,
                                     int hostsInActiveState,
                                     int hostsInReadyState)
        throws Throwable {

      for (int i = 0; i < totalHosts; i++) {
        // create datastore
        DatastoreService.State datastore = new DatastoreService.State();
        datastore.id = "datastore" + i;
        datastore.isImageDatastore = false;
        datastore.name = "datastore";
        datastore.type = "SHARED_VMFS";
        datastore.documentSelfLink = datastore.id;
        Operation operation = env.sendPostAndWaitForReplication(DatastoreServiceFactory.SELF_LINK, datastore);
        DatastoreService.State createdDatastore =
            operation.getBody(DatastoreService.State.class);
        datastoreSelfLinks.add(createdDatastore.documentSelfLink);

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

        if (i < hostsWithDatastore) {
          host.reportedDatastores.add(datastore.id);
        }
        if (i >= hostsInActiveState) {
          host.agentState = AgentState.MISSING;
        }
        if (i >= hostsInReadyState) {
          host.state = HostState.MAINTENANCE;
        }

        operation = env.sendPostAndWaitForReplication(HostServiceFactory.SELF_LINK, host);
        HostService.State createdHost =
            operation.getBody(HostService.State.class);
        hostSelfLinks.add(createdHost.documentSelfLink);
      }
    }

    private Long getTotalDatastoreCount(TestEnvironment environment) throws Throwable {
      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(DatastoreService.State.class)
              .build())
          .build();
      QueryTask result = environment.sendQueryAndWait(queryTask);
      return result.results.documentCount;
    }
  }
}
