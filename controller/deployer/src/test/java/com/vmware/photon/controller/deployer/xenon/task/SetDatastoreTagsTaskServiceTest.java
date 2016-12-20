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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements test for the
 * {@link SetDatastoreTagsTaskService} class.
 */
public class SetDatastoreTagsTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  private SetDatastoreTagsTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    SetDatastoreTagsTaskService.State startState = new SetDatastoreTagsTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }
    startState.idsToMatch = new ArrayList<>();
    startState.idsToMatch.add("id1");
    startState.tagsToAdd = new ArrayList<>();
    startState.tagsToAdd.add("tag1");
    return startState;
  }

  private SetDatastoreTagsTaskService.State buildValidPatchState(TaskState.TaskStage patchStage) {
    SetDatastoreTagsTaskService.State patchState = new SetDatastoreTagsTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;

    return patchState;
  }

  /**
   * Tests for constructors.
   */
  @Test
  public class InitializationTest {

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION
      );

      SetDatastoreTagsTaskService setDatastoreTagsTaskService = new SetDatastoreTagsTaskService();
      assertThat(setDatastoreTagsTaskService.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    private SetDatastoreTagsTaskService setDatastoreTagsTaskService;
    private TestHost testHost;
    private boolean serviceCreated = false;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      setDatastoreTagsTaskService = new SetDatastoreTagsTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      SetDatastoreTagsTaskService.State serviceState
          = testHost.getServiceState(SetDatastoreTagsTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(TaskState.TaskStage startStage) throws Throwable {
      SetDatastoreTagsTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = null;
      startService(startState);

      SetDatastoreTagsTaskService.State serviceState
          = testHost.getServiceState(SetDatastoreTagsTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(startStage));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testInvalidMatchParameters() throws Throwable {
      SetDatastoreTagsTaskService.State startState = buildValidStartState(null);
      startState.idsToMatch = new ArrayList<>();
      startState.idsToMatch.add("id1");
      startState.typeToMatch = DatastoreType.EXT3.toString();
      startService(startState);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testInvalidTagParameters() throws Throwable {
      SetDatastoreTagsTaskService.State startState = buildValidStartState(null);
      startState.tagsToAdd = null;
      startState.tagsToRemove = null;
      startService(startState);
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    private void startService(SetDatastoreTagsTaskService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(setDatastoreTagsTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private SetDatastoreTagsTaskService setDatastoreTagsTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      setDatastoreTagsTaskService = new SetDatastoreTagsTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {

      SetDatastoreTagsTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(setDatastoreTagsTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {

      SetDatastoreTagsTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(setDatastoreTagsTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }
  }

  /**
   * End-to-end tests.
   */
  public class EndToEndTest {

    private TestEnvironment testEnvironment = null;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreTestEnvironment;
    private SetDatastoreTagsTaskService.State startState;
    private ListeningExecutorService listeningExecutorService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreTestEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      startTestEnvironment();
      setupDatastoreServiceDocuments();

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.controlFlags = null;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (null != cloudStoreTestEnvironment) {
        cloudStoreTestEnvironment.stop();
        cloudStoreTestEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test
    public void testEndToEndSuccessForAllMatch() throws Throwable {
      // Try to remove a tag that does not exist
      // Try to remove a tag that does exist
      // Try to add a tag that exist
      // Try to add a tag that does not exist
      startState.idsToMatch = null;
      startState.typeToMatch = null;
      startState.tagsToAdd.add("tagnew");
      startState.tagsToRemove = new ArrayList<>();
      startState.tagsToRemove.add("tagToRemove");
      startState.tagsToRemove.add("tag2");

      SetDatastoreTagsTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          SetDatastoreTagsTaskFactoryService.SELF_LINK,
          startState,
          SetDatastoreTagsTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      //change tags.
      Set<DatastoreService.State> datastores = getDatastores();
      for (DatastoreService.State ds : datastores) {
        assertThat(ds.tags.contains("tagnew"), is(true));
        assertThat(ds.tags.contains("tagToRemove"), is(false));
        assertThat(ds.tags.contains("tag2"), is(false));
      }
    }

    private Set<DatastoreService.State> getDatastores() throws Throwable {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreTestEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      Set<DatastoreService.State> result = new HashSet<>();
      for (String documentLink : documentLinks) {
        DatastoreService.State state =
            cloudStoreTestEnvironment.getServiceState(documentLink, DatastoreService.State.class);
        result.add(state);
      }
      return result;
    }

    @Test
    public void testEndToEndSuccessUsingIDMatch() throws Throwable {
      // Try to remove a tag that does not exist
      // Try to remove a tag that does exist
      // Try to add a tag that exist
      // Try to add a tag that does not exist
      startState.idsToMatch = new ArrayList<>();
      startState.idsToMatch.add("id1");
      startState.idsToMatch.add("id2");
      startState.typeToMatch = null;
      startState.tagsToAdd.add("tagnew");
      startState.tagsToRemove = new ArrayList<>();
      startState.tagsToRemove.add("tagToRemove");
      startState.tagsToRemove.add("tag2");

      SetDatastoreTagsTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          SetDatastoreTagsTaskFactoryService.SELF_LINK,
          startState,
          SetDatastoreTagsTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      Set<DatastoreService.State> datastores = getDatastores();
      for (DatastoreService.State ds : datastores) {
        if (ds.id.equals("id1") || ds.id.equals("id2")) {
          assertThat(ds.tags.contains("tagnew"), is(true));
          assertThat(ds.tags.contains("tagToRemove"), is(false));
          assertThat(ds.tags.contains("tag2"), is(false));
        } else {
          assertThat(ds.tags.contains("tagnew"), is(false));
          assertThat(ds.tags.contains("tag2"), is(true));
        }
      }
    }

    @Test
    public void testEndToEndSuccessUsingTypeMatch() throws Throwable {
      // Try to remove a tag that does not exist
      // Try to remove a tag that does exist
      // Try to add a tag that exist
      // Try to add a tag that does not exist
      startState.idsToMatch = null;
      startState.typeToMatch = DatastoreType.EXT3.toString();
      startState.tagsToAdd.add("tagnew");
      startState.tagsToRemove = new ArrayList<>();
      startState.tagsToRemove.add("tagToRemove");
      startState.tagsToRemove.add("tag2");

      SetDatastoreTagsTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          SetDatastoreTagsTaskFactoryService.SELF_LINK,
          startState,
          SetDatastoreTagsTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      Set<DatastoreService.State> datastores = getDatastores();
      for (DatastoreService.State ds : datastores) {
        if (ds.id.equals("id1") || ds.id.equals("id3")) {
          assertThat(ds.tags.contains("tagnew"), is(true));
          assertThat(ds.tags.contains("tagToRemove"), is(false));
          assertThat(ds.tags.contains("tag2"), is(false));
        } else {
          assertThat(ds.tags.contains("tagnew"), is(false));
          assertThat(ds.tags.contains("tag2"), is(true));
        }
      }
    }

    @Test
    public void testFailOnNoMatch() throws Throwable {
      startState.idsToMatch = new ArrayList<>();
      startState.idsToMatch.add("id4");
      startState.typeToMatch = null;
      startState.tagsToAdd.add("tagnew");

      SetDatastoreTagsTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          SetDatastoreTagsTaskFactoryService.SELF_LINK,
          startState,
          SetDatastoreTagsTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Could not find a datastore to match the query."));

      Set<DatastoreService.State> datastores = getDatastores();
      for (DatastoreService.State ds : datastores) {
        if (ds.tags != null) {
          assertThat(ds.tags.contains("tagnew"), is(false));
        }
      }
    }

    private void startTestEnvironment() throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreTestEnvironment.getServerSet())
          .hostCount(1)
          .build();
    }


    private void setupDatastoreServiceDocuments() throws Throwable {
      DatastoreService.State datastoreState1 = new DatastoreService.State();
      datastoreState1.id = "id1";
      datastoreState1.documentSelfLink = datastoreState1.id;
      datastoreState1.type = DatastoreType.EXT3.toString();
      datastoreState1.name = "name1";

      DatastoreService.State datastoreState2 = new DatastoreService.State();
      datastoreState2.id = "id2";
      datastoreState2.documentSelfLink = datastoreState2.id;
      datastoreState2.type = DatastoreType.LOCAL_VMFS.toString();
      datastoreState2.tags = new HashSet<>();
      datastoreState2.tags.add("tag1");
      datastoreState2.tags.add("tag2");
      datastoreState2.tags.add("tag3");
      datastoreState2.name = "name2";

      DatastoreService.State datastoreState3 = new DatastoreService.State();
      datastoreState3.id = "id3";
      datastoreState3.documentSelfLink = datastoreState3.id;
      datastoreState3.type = DatastoreType.EXT3.toString();
      datastoreState3.tags = new HashSet<>();
      datastoreState3.tags.add("tag1");
      datastoreState3.tags.add("tag2");
      datastoreState3.tags.add("tag3");
      datastoreState3.name = "name3";

      cloudStoreTestEnvironment.callServiceSynchronously(
          DatastoreServiceFactory.SELF_LINK,
          datastoreState1,
          DatastoreService.State.class
      );

      cloudStoreTestEnvironment.callServiceSynchronously(
          DatastoreServiceFactory.SELF_LINK,
          datastoreState2,
          DatastoreService.State.class
      );

      cloudStoreTestEnvironment.callServiceSynchronously(
          DatastoreServiceFactory.SELF_LINK,
          datastoreState3,
          DatastoreService.State.class
      );
    }
  }
}
