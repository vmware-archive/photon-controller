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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.TaskState.TaskStage;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.NodeGroupBroadcastResponse;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

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

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "fieldNamesWithMissingValue")
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
       CopyStateTriggerTaskService.State patch = new CopyStateTriggerTaskService.State();
       patch.pulse = Boolean.TRUE;
       destinationCluster.sendPatchAndWait(state.documentSelfLink, patch);

       CopyStateTriggerTaskService.State currentState = destinationCluster
           .getServiceState(state.documentSelfLink, CopyStateTriggerTaskService.State.class);
       currentState = waitForTriggerToFinish(currentState);

       NodeGroupBroadcastResponse response = destinationCluster.
           sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
       List<CopyStateTaskService.State> documents = QueryTaskUtils
           .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

       assertThat(documents.size(), is(1));
       assertThat(copyStateHasCorrectValues(documents.get(0), state), is(true));
    }

    @Test
    public void successStartsOnlyOneCopyStateService() throws Throwable {
       startClusters();

       CopyStateTriggerTaskService.State state = destinationCluster
           .callServiceSynchronously(
               CopyStateTriggerTaskFactoryService.SELF_LINK,
               startState,
               CopyStateTriggerTaskService.State.class);
       CopyStateTriggerTaskService.State patch = new CopyStateTriggerTaskService.State();
       patch.pulse = Boolean.TRUE;
       destinationCluster.sendPatchAndWait(state.documentSelfLink, patch);
       Thread.sleep(100);
       destinationCluster.sendPatchAndWait(state.documentSelfLink, patch);

       CopyStateTriggerTaskService.State currentState = destinationCluster
           .getServiceState(state.documentSelfLink, CopyStateTriggerTaskService.State.class);
       currentState = waitForTriggerToFinish(currentState);

       NodeGroupBroadcastResponse response = destinationCluster
           .sendBroadcastQueryAndWait(generateQueryCopyStateTaskQuery());
       List<CopyStateTaskService.State> documents = QueryTaskUtils
           .getBroadcastQueryDocuments(CopyStateTaskService.State.class, response);

       assertThat(documents.size(), is(1));
       assertThat(copyStateHasCorrectValues(documents.get(0), state), is(true));
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
       patch.pulse = Boolean.TRUE;
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
      Thread.sleep(120);

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
      startState.sourcePort = 1234;
      startState.sourceIp = "127.0.0.1";
      startState.destinationPort = 4321;
      startState.destinationIp = "127.0.0.1";
      startState.factoryLink = "fake";
      startState.sourceFactoryLink = "fake";
      return startState;
    }

    private CopyStateTriggerTaskService.State waitForTriggerToFinish(CopyStateTriggerTaskService.State state)
        throws Throwable {
      CopyStateTriggerTaskService.State currentState = state;
      while (currentState.triggersSuccess + currentState.triggersError == 0) {
         currentState = destinationCluster
             .getServiceState(state.documentSelfLink, CopyStateTriggerTaskService.State.class);
       }
      return currentState;
    }

    private void startClusters() throws Throwable {
      sourceCluster = new TestEnvironment.Builder().hostCount(1).build();
      destinationCluster = new TestEnvironment.Builder().hostCount(1).build();
      startState.sourcePort = sourceCluster.getHosts()[0].getPort();
      startState.destinationPort = destinationCluster.getHosts()[0].getPort();
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
      return state.destinationIp.equals(startState.destinationIp)
          && state.destinationPort.equals(startState.destinationPort)
          && state.destinationProtocol.equals(startState.destinationProtocol)
          && state.sourceIp.equals(startState.sourceIp)
          && state.sourcePort.equals(startState.sourcePort)
          && state.sourceProtocol.equals(startState.sourceProtocol)
          && state.factoryLink.equals(startState.factoryLink + "/")
          && state.sourceFactoryLink.equals(startState.sourceFactoryLink + "/");
    }
  }

  private CopyStateTriggerTaskService.State buildValidStartState() {
    CopyStateTriggerTaskService.State state = new CopyStateTriggerTaskService.State();
    state.sourceIp = "0.0.0.0";
    state.sourceFactoryLink = ContainerFactoryService.SELF_LINK;
    state.sourcePort = 1234;
    state.destinationIp = "0.0.0.0";
    state.factoryLink = ContainerFactoryService.SELF_LINK;
    state.destinationPort = 1234;
    return state;
  }
}
