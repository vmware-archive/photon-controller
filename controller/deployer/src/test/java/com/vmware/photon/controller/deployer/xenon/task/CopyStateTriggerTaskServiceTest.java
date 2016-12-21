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

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements tests for the {@link CopyStateTriggerTaskService} class.
 */
public class CopyStateTriggerTaskServiceTest {
  private CopyStateTriggerTaskService service;
  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CopyStateTriggerTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test
    public void testValidStartStages() throws Throwable {
      CopyStateTriggerTaskService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      CopyStateTriggerTaskService.State startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CopyStateTriggerTaskService.State.class,
              NotNull.class));
    }
  }

  /**
   * This class implements end to end tests.
   */
  public class EndToEndTest {
    private TestEnvironment sourceCluster;
    private TestEnvironment destinationCluster;
    private CopyStateTriggerTaskService.State startState;

    @BeforeMethod
    public void setupMethod() {
      startState = buildValidStartState();
    }

    @AfterMethod
    public void tearDownMethod() throws Throwable {
      if (sourceCluster != null) {
        sourceCluster.stop();
      }
      if (destinationCluster != null) {
        destinationCluster.stop();
      }
    }

    @Test
    public void successStartsCopyStateService() throws Throwable {
      startClusters();

      CopyStateTriggerTaskService.State state = destinationCluster
          .callServiceSynchronously(
              CopyStateTriggerTaskFactoryService.SELF_LINK,
              startState,
              CopyStateTriggerTaskService.State.class);

      ServiceHostUtils.waitForServiceState(CopyStateTriggerTaskService.State.class,
          state.documentSelfLink,
          (CopyStateTriggerTaskService.State result) -> result.triggersSuccess > 0,
          destinationCluster.getHosts()[0],
          TimeUnit.SECONDS.toMillis(1),
          10,
          null
      );

      NodeGroupBroadcastResponse response = destinationCluster.
          sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
      List<CopyStateTaskService.State> documents = QueryTaskUtils
          .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

      assertThat(documents.size(), is(greaterThanOrEqualTo(1)));
      assertThat(copyStateHasCorrectValues(documents.get(0), state), is(true));
    }

    @Test
    public void successStartsOnlyOneCopyStateService() throws Throwable {
      startClusters();

      CopyStateTriggerTaskService.State state = buildValidStartState();

      CopyStateTaskService.State seedCopyTask = buildValidCopyTaskStartState(TaskStage.CREATED);
      destinationCluster
          .callServiceSynchronously(
              CopyStateTaskFactoryService.SELF_LINK,
              seedCopyTask,
              CopyStateTaskService.State.class);

      startState.enableMaintenance = false;

      state = destinationCluster
          .callServiceSynchronously(
              CopyStateTriggerTaskFactoryService.SELF_LINK,
              state,
              CopyStateTriggerTaskService.State.class);

      CopyStateTriggerTaskService.State patch = new CopyStateTriggerTaskService.State();
      destinationCluster.sendPatchAndWait(state.documentSelfLink, patch);

      ServiceHostUtils.waitForServiceState(ServiceStats.class,
          state.documentSelfLink + "/stats",
          (ServiceStats result) -> {
            ServiceStats.ServiceStat serviceStat = result.entries.get(CopyStateTriggerTaskService.STAT_NAME_SKIP_COUNT);
            return serviceStat != null && serviceStat.latestValue > 0;
          },
          destinationCluster.getHosts()[0],
          TimeUnit.SECONDS.toMillis(1),
          10,
          null
      );

      NodeGroupBroadcastResponse response = destinationCluster
          .sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
      List<CopyStateTaskService.State> documents = QueryTaskUtils
          .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

      assertThat(documents.size(), is(1));
    }

    @Test
    public void shouldNotStartCopyStateWhenStopped() throws Throwable {
      startClusters();
      startState.executionState = CopyStateTriggerTaskService.ExecutionState.STOPPED;

      CopyStateTriggerTaskService.State state = destinationCluster
          .callServiceSynchronously(
              CopyStateTriggerTaskFactoryService.SELF_LINK,
              startState,
              CopyStateTriggerTaskService.State.class);
      CopyStateTriggerTaskService.State patch = new CopyStateTriggerTaskService.State();
      destinationCluster.sendPatchAndWait(state.documentSelfLink, patch);
      Thread.sleep(20);

      NodeGroupBroadcastResponse response = destinationCluster
          .sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
      List<CopyStateTaskService.State> documents = QueryTaskUtils
          .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

      assertThat(documents.size(), is(0));
    }

    @Test
    public void shouldIgnoreIrelevantCopyStateTasks() throws Throwable {
      startClusters();
      destinationCluster.callServiceSynchronously(
          CopyStateTaskFactoryService.SELF_LINK,
          buildCopyStateTaskState(TaskStage.STARTED),
          CopyStateTaskService.State.class);

      destinationCluster.callServiceSynchronously(
          CopyStateTriggerTaskFactoryService.SELF_LINK,
          startState,
          CopyStateTriggerTaskService.State.class);

      NodeGroupBroadcastResponse response = destinationCluster
          .sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
      List<CopyStateTaskService.State> documents = QueryTaskUtils
          .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

      assertThat(documents.size(), is(1));
    }

    private CopyStateTaskService.State buildCopyStateTaskState(TaskState.TaskStage stage) {
      CopyStateTaskService.State startState = new CopyStateTaskService.State();
      startState.taskState = new TaskState();
      startState.taskState.stage = stage;
      startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
      startState.sourceURIs = Collections.singletonList(UriUtils.buildUri("http://127.0.0.1:1234"));
      startState.sourceFactoryLink = "fake";
      startState.destinationURI = UriUtils.buildUri("http://127.0.0.1:4321");
      startState.destinationFactoryLink = "fake";
      return startState;
    }

    private void startClusters() throws Throwable {
      sourceCluster = new TestEnvironment.Builder().hostCount(1).build();
      destinationCluster = new TestEnvironment.Builder().hostCount(1).build();
      startState.destinationURI = (destinationCluster.getHosts()[0]).getUri();
      startState.sourceURIs = Stream.of(sourceCluster.getHosts())
          .map((host) -> host.getUri()).collect(Collectors.toList());
    }

    private QueryTask generateQueryCopyStateTaskQuery() {
      QueryTask.Query typeClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(CopyStateTaskService.State.class));
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = typeClause;
      querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      return QueryTask.create(querySpecification).setDirect(true);
    }

    private boolean copyStateHasCorrectValues(
        CopyStateTaskService.State state,
        CopyStateTriggerTaskService.State startState) {
      return state.sourceURIs.equals(startState.sourceURIs)
          && state.sourceFactoryLink.equals(startState.sourceFactoryLink)
          && state.destinationURI.equals(startState.destinationURI)
          && state.destinationFactoryLink.equals(startState.destinationFactoryLink);
    }
  }

  private CopyStateTriggerTaskService.State buildValidStartState() {
    CopyStateTriggerTaskService.State state = new CopyStateTriggerTaskService.State();
    state.sourceURIs = Collections.singletonList(UriUtils.buildUri("http://127.0.0.1:1234"));
    state.sourceFactoryLink = ContainerFactoryService.SELF_LINK;
    state.destinationURI = UriUtils.buildUri("http://127.0.0.1:4321");
    state.destinationFactoryLink = ContainerFactoryService.SELF_LINK;
    state.enableMaintenance = true;
    state.maintenanceIntervalMicros = TimeUnit.MILLISECONDS.toMicros(50);
    return state;
  }

  private CopyStateTaskService.State buildValidCopyTaskStartState(TaskState.TaskStage stage) {
    CopyStateTaskService.State startState = new CopyStateTaskService.State();
    startState.taskState = new TaskState();
    startState.taskState.stage = stage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.sourceURIs = Collections.singletonList(UriUtils.buildUri("http://127.0.0.1:1234"));
    startState.sourceFactoryLink = ContainerFactoryService.SELF_LINK;
    startState.destinationURI = UriUtils.buildUri("http://127.0.0.1:4321");
    startState.destinationFactoryLink = ContainerFactoryService.SELF_LINK;
    return startState;
  }
}
