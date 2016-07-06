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
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
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
import java.util.EnumSet;
import java.util.HashSet;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.xenon.task.DatastoreDeleteService}.
 */
public class DatastoreDeleteServiceTest {

  private BasicServiceHost host;
  private DatastoreDeleteService service;

  private DatastoreDeleteService.State buildValidStartupState() {
    DatastoreDeleteService.State state = new DatastoreDeleteService.State();
    state.datastoreId = "datastoreId";
    state.parentServiceLink = "parentServiceLink";
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
      service = new DatastoreDeleteService();
    }

    @Test
    public void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
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
      service = new DatastoreDeleteService();
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
      DatastoreDeleteService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DatastoreDeleteService.State savedState =
          host.getServiceState(DatastoreDeleteService.State.class);
      assertThat(savedState.documentSelfLink, is(BasicServiceHost.SERVICE_URI));
      assertThat(savedState.datastoreId,
          is(startState.datastoreId));
    }

    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      DatastoreDeleteService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DatastoreDeleteService.State savedState =
          host.getServiceState(DatastoreDeleteService.State.class);
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
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredFieldName(String fieldName) throws Throwable {
      DatastoreDeleteService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);
      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return new Object[][]{
          {"datastoreId"},
          {"parentServiceLink"}
      };
    }

  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    DatastoreDeleteService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();

      service = new DatastoreDeleteService();
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
    private DatastoreDeleteService.State request;

    @BeforeMethod
    public void setUp() throws Throwable {
      // Build input.
      request = buildValidStartupState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        // Note that this will fully clean up the Xenon host's Lucene index: all
        // services we created will be fully removed.
        machine.stop();
        machine = null;
      }
    }

    // Tests the success scenario, where
    // - if there are active hosts through which a datastore can be accessed, do nothing
    // - if there are 0 active hosts through which a datastore can be accessed, delete it
    @Test(dataProvider = "Success")
    public void testSuccess(
        int totalHosts,
        int hostsWithDatastore,
        int hostsInActiveState,
        int hostsInReadyState)
        throws Throwable {

      machine = TestEnvironment.create(1);
      seedTestEnvironment(
          machine,
          totalHosts,
          hostsWithDatastore,
          hostsInActiveState,
          hostsInReadyState);

      DatastoreDeleteService.State response = machine.callServiceAndWaitForState(
          DatastoreDeleteFactoryService.SELF_LINK,
          request,
          DatastoreDeleteService.State.class,
          (DatastoreDeleteService.State state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);

      assertThat(response.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(response.activeHostCount, is(hostsWithDatastore));
      if (hostsWithDatastore > 0) {
        assertThat(getTotalDocumentCount(machine, DatastoreService.State.class), is(1L));
        assertThat(getTotalDocumentCount(machine, ImageToImageDatastoreMappingService.State.class), is(1L));
      } else {
        assertThat(getTotalDocumentCount(machine, DatastoreService.State.class), is(0L));
        assertThat(getTotalDocumentCount(machine, ImageToImageDatastoreMappingService.State.class), is(0L));
      }
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
      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = "datastoreId";
      datastore.isImageDatastore = true;
      datastore.name = "datastore";
      datastore.type = "SHARED_VMFS";
      datastore.documentSelfLink = "datastoreId";
      env.sendPostAndWaitForReplication(DatastoreServiceFactory.SELF_LINK, datastore);

      ImageToImageDatastoreMappingService.State mapping = new ImageToImageDatastoreMappingService.State();
      mapping.imageId = "image-id";
      mapping.imageDatastoreId = datastore.id;
      env.sendPostAndWaitForReplication(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, mapping);

      for (int i = 0; i < totalHosts; i++) {
        // create hosts
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
          host.reportedDatastores.add("datastoreId");
        }
        if (i >= hostsInActiveState) {
          host.agentState = AgentState.MISSING;
        }
        if (i >= hostsInReadyState) {
          host.state = HostState.MAINTENANCE;
        }

        env.sendPostAndWaitForReplication(HostServiceFactory.SELF_LINK, host);
      }
    }

    private Long getTotalDocumentCount(TestEnvironment environment, Class kind) throws Throwable {
      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(kind)
              .build())
          .build();
      QueryTask result = environment.sendQueryAndWait(queryTask);
      return result.results.documentCount;
    }
  }
}
