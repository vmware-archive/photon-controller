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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerContextTest;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowServiceTest;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployStageStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatusCode;
import com.vmware.photon.controller.deployer.gen.Deployment;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentRequest;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Implements tests for {@link DeploymentWorkFlowServiceClient}.
 */
public class DeploymentWorkFlowServiceClientTest {

  @Test
  public void dummy() {
  }

  /**
   * This class tests the create method.
   */
  public class CreateDeployWorkflowEntity {

    private DeploymentWorkFlowServiceClient target;
    private DeployerXenonServiceHost host;
    private DeploymentWorkflowService.State serviceDocument;
    private DeployerConfig deployerConfig;
    private ContainersConfig containerConfig;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      deployerConfig = mock(DeployerConfig.class);
      containerConfig = mock(ContainersConfig.class);
      doReturn(containerConfig).when(deployerConfig).getContainersConfig();
      target = new DeploymentWorkFlowServiceClient(deployerConfig, host);
      serviceDocument = new DeploymentWorkflowService.State();
      serviceDocument.documentSelfLink = "self-link";
    }

    @Test
    public void success() throws Throwable {
      DeployRequest request = createDeployRequest();

      setupMock(
          true,
          DeploymentWorkflowFactoryService.SELF_LINK,
          DeploymentWorkflowService.State.class,
          null,
          serviceDocument,
          host);

      String link = target.create(request);
      assertThat(link, is("self-link"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsWhenSetDesiredStateThrowsException() throws Throwable {
      DeployRequest request = createDeployRequest();
      request.setDesired_state("INVALID");

      setupMock(
          true,
          DeploymentWorkflowFactoryService.SELF_LINK,
          DeploymentWorkflowService.State.class,
          null,
          serviceDocument,
          host);

      target.create(request);
    }

    @Test(expectedExceptions = Exception.class)
    public void failsWhenDcpHostThrowsException() throws Throwable {
      DeployRequest request = createDeployRequest();

      setupMock(
          false,
          DeploymentWorkflowFactoryService.SELF_LINK,
          DeploymentWorkflowService.State.class,
          null,
          null,
          host);

      target.create(request);
    }

    private DeployRequest createDeployRequest() {
      DeployRequest request = new DeployRequest();
      request.setDeployment(new Deployment());
      request.setDesired_state("PAUSED");
      return request;
    }
  }

  /**
   * This class tests the has method.
   */
  public class Has {

    TestEnvironment testEnvironment;
    DeployerContext deployerContext;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          DeployerContextTest.class.getResource("/config.yml").getPath()).getDeployerContext();

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    @Test(dataProvider = "runningStage")
    public void successHasWithDeployment(
        TaskState.TaskStage startStage,
        DeploymentWorkflowService.TaskState.SubStage startSubStage) throws Throwable {

      DeploymentWorkflowService.State startState =
          DeploymentWorkflowServiceTest.buildValidStartState(startStage, startSubStage);

      testEnvironment.callServiceSynchronously(
          DeploymentWorkflowFactoryService.SELF_LINK,
          startState,
          DeploymentWorkflowService.State.class);

      DeploymentWorkFlowServiceClient client =
          new DeploymentWorkFlowServiceClient(null, testEnvironment.getHosts()[0]);

      assertThat(client.has(), is(true));
    }

    @DataProvider(name = "runningStage")
    public Object[][] getRunningStage() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},
      };
    }

    @Test
    public void successHasWithoutDeployment() throws Throwable {
      DeploymentWorkFlowServiceClient client =
          new DeploymentWorkFlowServiceClient(null, testEnvironment.getHosts()[0]);
      assertThat(client.has(), is(false));
    }
  }

  /**
   * This class tests the remove deployment method.
   */
  public static class RemoveDeploymentWorkflowEntity {

    private DeploymentWorkFlowServiceClient target;
    private DeployerXenonServiceHost host;
    private ServiceDocument serviceDocument;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      target = new DeploymentWorkFlowServiceClient(mock(DeployerConfig.class), host);
      serviceDocument = new ServiceDocument();
      serviceDocument.documentSelfLink = "self-link";
    }

    @Test
    public void success() throws Throwable {
      RemoveDeploymentRequest request = createRemoveDeploymentRequest();
      doAnswer(new Answer() {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];
          op.setBody(serviceDocument);
          op.complete();
          return null;
        }
      }).when(host).sendRequest(any(Operation.class));

      String link = target.remove(request);
      assertThat(link, is("self-link"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsWhenDcpHostThrowsException() throws Throwable {
      RemoveDeploymentRequest request = createRemoveDeploymentRequest();

      doThrow((new RuntimeException())).when(host).sendRequest(any(Operation.class));

      target.remove(request);
    }

    private RemoveDeploymentRequest createRemoveDeploymentRequest() {
      RemoveDeploymentRequest request = new RemoveDeploymentRequest();
      return request;
    }
  }

  /**
   * This class tests the create method.
   */
  public class GetStatus {

    private DeploymentWorkFlowServiceClient target;
    private DeployerXenonServiceHost host;
    private DeployerConfig deployerConfig;
    private ContainersConfig containerConfig;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      deployerConfig = mock(DeployerConfig.class);
      containerConfig = mock(ContainersConfig.class);
      doReturn(containerConfig).when(deployerConfig).getContainersConfig();
      target = new DeploymentWorkFlowServiceClient(deployerConfig, host);
    }

    @Test(dataProvider = "DeploymentStages")
    public void testDeploymentState(TaskState.TaskStage startStage,
                                    DeploymentWorkflowService.TaskState.SubStage startSubStage,
                                    List<DeployStageStatus> stages) throws Throwable {

      DeploymentWorkflowService.State startState =
          DeploymentWorkflowServiceTest.buildValidStartState(startStage, startSubStage);

      if (TaskState.TaskStage.FAILED == startStage) {
        // Mock failure during management plane creation
        startState.taskState.failure = Utils.toServiceErrorResponse(new RuntimeException("Management plane error"));
        startState.taskSubStates = new ArrayList<>();
        for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
          switch (s) {
            case PROVISION_MANAGEMENT_HOSTS:
            case CREATE_MANAGEMENT_PLANE:
            case PROVISION_CLOUD_HOSTS:
            case ALLOCATE_CM_RESOURCES:
              startState.taskSubStates.add(TaskState.TaskStage.FINISHED);
              break;
            case MIGRATE_DEPLOYMENT_DATA:
              startState.taskSubStates.add(TaskState.TaskStage.FAILED);
              break;
            case SET_DEPLOYMENT_STATE:
              startState.taskSubStates.add(null);
              break;
          }
        }
      }

      setupMock(
          true,
          "request_id",
          DeploymentWorkflowService.State.class,
          null,
          startState,
          host);

      DeployStatus deployStatus = target.getDeployStatus("request_id");

      assertThat(deployStatus.getCode(),
          is(DeploymentWorkFlowServiceClient.getDeployStatusCodeForTaskStage(startStage)));
      assertThat(deployStatus.getStages(), is(stages));

      if (TaskState.TaskStage.FAILED == startStage) {
        assertThat(deployStatus.getError(), containsString("Management plane error"));
      } else if (TaskState.TaskStage.CANCELLED == startStage) {
        assertThat(deployStatus.getError(), is("Deployment was cancelled"));
      }
    }

    @DataProvider(name = "DeploymentStages")
    public Object[][] getDeploymentStages() {

      ArrayList<DeployStageStatus> addHostStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
            status.setCode(DeployStatusCode.IN_PROGRESS);
            break;
          default:
            status.setCode(null);
            break;
        }

        addHostStages.add(status);
      }

      ArrayList<DeployStageStatus> createMgmtPlaneStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          case CREATE_MANAGEMENT_PLANE:
            status.setCode(DeployStatusCode.IN_PROGRESS);
            break;
          default:
            status.setCode(null);
            break;
        }

        createMgmtPlaneStages.add(status);
      }

      ArrayList<DeployStageStatus> provisionCloudHosts = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
          case CREATE_MANAGEMENT_PLANE:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          case PROVISION_CLOUD_HOSTS:
            status.setCode(DeployStatusCode.IN_PROGRESS);
            break;
          default:
            status.setCode(null);
            break;
        }

        provisionCloudHosts.add(status);
      }

      ArrayList<DeployStageStatus> allocateCMStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
          case CREATE_MANAGEMENT_PLANE:
          case PROVISION_CLOUD_HOSTS:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          case ALLOCATE_CM_RESOURCES:
            status.setCode(DeployStatusCode.IN_PROGRESS);
            break;
          default:
            status.setCode(null);
            break;
        }

        allocateCMStages.add(status);
      }

      ArrayList<DeployStageStatus> migrateDataStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
          case CREATE_MANAGEMENT_PLANE:
          case PROVISION_CLOUD_HOSTS:
          case ALLOCATE_CM_RESOURCES:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          case MIGRATE_DEPLOYMENT_DATA:
            status.setCode(DeployStatusCode.IN_PROGRESS);
            break;
          default:
            status.setCode(null);
            break;
        }

        migrateDataStages.add(status);
      }

      ArrayList<DeployStageStatus> finishedStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
          case CREATE_MANAGEMENT_PLANE:
          case PROVISION_CLOUD_HOSTS:
          case ALLOCATE_CM_RESOURCES:
          case MIGRATE_DEPLOYMENT_DATA:
          case SET_DEPLOYMENT_STATE:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          default:
            status.setCode(null);
            break;
        }

        finishedStages.add(status);
      }

      ArrayList<DeployStageStatus> failedStages = new ArrayList<>();
      for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
        DeployStageStatus status = new DeployStageStatus();
        status.setName(s.name());
        switch (s) {
          case PROVISION_MANAGEMENT_HOSTS:
          case CREATE_MANAGEMENT_PLANE:
          case PROVISION_CLOUD_HOSTS:
          case ALLOCATE_CM_RESOURCES:
            status.setCode(DeployStatusCode.FINISHED);
            break;
          case MIGRATE_DEPLOYMENT_DATA:
            status.setCode(DeployStatusCode.FAILED);
            break;
          default:
            status.setCode(null);
            break;
        }

        failedStages.add(status);
      }

      return new Object[][]{
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              addHostStages},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              createMgmtPlaneStages},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              provisionCloudHosts},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              allocateCMStages},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              migrateDataStages},
          {TaskState.TaskStage.FINISHED, null, finishedStages},
          {TaskState.TaskStage.FAILED, null, failedStages},
      };
    }

    @Test(expectedExceptions = Exception.class)
    public void failsToCreateWhenDcpHostThrowsException() throws Throwable {
      doThrow((new RuntimeException())).when(host).sendRequest(any(Operation.class));
      target.getDeployStatus("request_id");
    }

    @Test
    public void successRemove() throws Throwable {
      //TODO() : Implement this after implementing RemoveDeploymentWorkflow

//      doAnswer(new Answer() {
//        @Override
//        public Object answer(InvocationOnMock invocation) throws Throwable {
//          Operation op = (Operation) invocation.getArguments()[0];
//          op.setBody(serviceState);
//          op.complete();
//          return null;
//        }
//      }).when(host).sendRequest(any(Operation.class));
//
//      RemoveDeploymentStatus removeDeploymentStatus = target.getRemoveDeploymentStatus("request_id");
//
//      assertThat(removeDeploymentStatus.getCode(), is(RemoveDeploymentStatusCode.FINISHED));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsToRemoveWhenDcpHostThrowsException() throws Throwable {
      doThrow((new RuntimeException())).when(host).sendRequest(any(Operation.class));
      target.getRemoveDeploymentStatus("request_id");
    }
  }

  /**
   * This class tests the initialize migrate deployment method.
   */
  public static class InitializeMigrateDeploymentTest {

    private DeploymentWorkFlowServiceClient target;
    private DeployerXenonServiceHost host;
    private ServiceDocument serviceDocument;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      target = new DeploymentWorkFlowServiceClient(mock(DeployerConfig.class), host);
      serviceDocument = new ServiceDocument();
      serviceDocument.documentSelfLink = "self-link";
    }

    @Test
    public void success() throws Throwable {
      InitializeMigrateDeploymentRequest request = createRequest();
      doAnswer(new Answer() {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];
          op.setBody(serviceDocument);
          op.complete();
          return null;
        }
      }).when(host).sendRequest(any(Operation.class));

      String link = target.initializeMigrateDeployment(request);
      assertThat(link, is("self-link"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsWhenDcpHostThrowsException() throws Throwable {
      InitializeMigrateDeploymentRequest request = createRequest();

      doThrow((new RuntimeException())).when(host).sendRequest(any(Operation.class));

      target.initializeMigrateDeployment(request);
    }

    private InitializeMigrateDeploymentRequest createRequest() {
      InitializeMigrateDeploymentRequest request = new InitializeMigrateDeploymentRequest();
      return request;
    }
  }

  /**
   * This class tests the finalize migrate deployment method.
   */
  public static class FinalizeMigrateDeploymentTest {

    private DeploymentWorkFlowServiceClient target;
    private DeployerXenonServiceHost host;
    private ServiceDocument serviceDocument;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      target = new DeploymentWorkFlowServiceClient(mock(DeployerConfig.class), host);
      serviceDocument = new ServiceDocument();
      serviceDocument.documentSelfLink = "self-link";
    }

    @Test
    public void success() throws Throwable {
      FinalizeMigrateDeploymentRequest request = createRequest();
      doAnswer(new Answer() {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];
          op.setBody(serviceDocument);
          op.complete();
          return null;
        }
      }).when(host).sendRequest(any(Operation.class));

      String link = target.finalizeMigrateDeployment(request);
      assertThat(link, is("self-link"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsWhenDcpHostThrowsException() throws Throwable {
      FinalizeMigrateDeploymentRequest request = createRequest();

      doThrow((new RuntimeException())).when(host).sendRequest(any(Operation.class));

      target.finalizeMigrateDeployment(request);
    }

    private FinalizeMigrateDeploymentRequest createRequest() {
      FinalizeMigrateDeploymentRequest request = new FinalizeMigrateDeploymentRequest();
      return request;
    }
  }

  private <T> void setupMock(
      boolean isSuccess,
      final String operationUri,
      final Class<T> documentType,
      final Predicate<T> assertion,
      final T returnedDocument,
      DeployerXenonServiceHost host) {

    ArgumentMatcher<Operation> opMatcher = new ArgumentMatcher<Operation>() {
      @Override
      public boolean matches(Object argument) {
        Operation op = (Operation) argument;

        return op.getUri().toString().contains(operationUri);
      }
    };

    if (isSuccess) {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];

          if (assertion != null && op.getBodyRaw() != null) {
            T document = op.getBody(documentType);
            assertion.test(document);
          }

          op.setBody(returnedDocument);
          op.complete();
          return op;
        }
      }).when(host).sendRequest(argThat(opMatcher));
    } else {
      doThrow((new RuntimeException())).when(host).sendRequest(argThat(opMatcher));
    }
  }
}
