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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddManagementHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddManagementHostWorkflowService;
import com.vmware.photon.controller.deployer.gen.ProvisionHostRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatus;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusRequest;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.Collections;
import java.util.function.Predicate;

/**
 * This class tests the {@link AddHostWorkflowServiceClient}.
 */
public class AddHostWorkflowServiceClientTest {

  @Test
  private void dummy() {
  }

  /**
   * This class tests the create method.
   */
  public class CreateAddCloudHostTaskEntity {

    private AddHostWorkflowServiceClient target;
    private DeployerDcpServiceHost host;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;


    @BeforeMethod
    public void before() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      testEnvironment = new TestEnvironment.Builder().cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1).build();
      host = spy(testEnvironment.getHosts()[0]);
      target = new AddHostWorkflowServiceClient(host);
    }

    @AfterMethod
    public void after() throws Throwable {
      cloudStoreMachine.stop();
      testEnvironment.stop();
    }

    @Test
    public void successCreateCloudHost() throws Throwable {
      ProvisionHostRequest request = createProvisionHostRequest();
      HostService.State hostService = TestHelper.createHostService(cloudStoreMachine, Collections.singleton(
          UsageTag.CLOUD.name()));

      AddCloudHostWorkflowService.State returnedDocument = new AddCloudHostWorkflowService.State();
      returnedDocument.hostServiceLink = hostService.documentSelfLink;
      returnedDocument.documentSelfLink = "task-id";

      setupMock(
          host,
          true,
          AddCloudHostWorkflowFactoryService.SELF_LINK,
          AddCloudHostWorkflowService.State.class,
          (state) -> {
            assertThat(state.hostServiceLink, is(hostService.documentSelfLink));
            return true;
          },
          returnedDocument);

      String taskLink = target.create(hostService.documentSelfLink);
      assertThat(taskLink, is("task-id"));
    }

    @Test
    public void successCreateManagementHost() throws Throwable {
      ProvisionHostRequest request = createProvisionHostRequest();
      HostService.State hostService = TestHelper.createHostService(cloudStoreMachine, ImmutableSet.of(
          UsageTag.MGMT.name(), UsageTag.CLOUD.name()));

      AddManagementHostWorkflowService.State returnedDocument = new AddManagementHostWorkflowService.State();
      returnedDocument.hostServiceLink = hostService.documentSelfLink;
      returnedDocument.documentSelfLink = "task-id";

      setupMock(
          host,
          true,
          AddManagementHostWorkflowFactoryService.SELF_LINK,
          AddManagementHostWorkflowService.State.class,
          (state) -> {
            assertThat(state.hostServiceLink, is(hostService.documentSelfLink));
            return true;
          },
          returnedDocument);

      String taskLink = target.create(hostService.documentSelfLink);
      assertThat(taskLink, is("task-id"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsCreateWhenDcpHostThrowsException() throws Throwable {
      ProvisionHostRequest request = createProvisionHostRequest();

      setupMock(
          host,
          false,
          AddCloudHostWorkflowFactoryService.SELF_LINK,
          AddCloudHostWorkflowService.State.class,
          null,
          null);
      target.create(request.getHost_id());
    }
  }

  /**
   * This class tests the getStatus method.
   */
  public class GetStatusAddCloudHostTask {

    private AddHostWorkflowServiceClient target;
    private DeployerDcpServiceHost host;

    @BeforeMethod
    public void before() {
      host = mock(DeployerDcpServiceHost.class);
      target = new AddHostWorkflowServiceClient(host);
    }

    @Test
    public void successGetStatus() throws Throwable {
      ProvisionHostStatusRequest request = createProvisionHostStatusRequest();

      AddCloudHostWorkflowService.State returnedDocument = new AddCloudHostWorkflowService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = new TaskState();
      returnedDocument.taskState.stage = TaskState.TaskStage.FINISHED;

      setupMock(
          host,
          true,
          "task-id",
          AddCloudHostWorkflowService.State.class,
          null,
          returnedDocument);

      ProvisionHostStatus status = target.getStatus(request.getOperation_id());
      assertThat(status.getResult(), is(ProvisionHostStatusCode.FINISHED));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsGetStatusWhenDcpHostThrowsException() throws Throwable {
      ProvisionHostStatusRequest request = createProvisionHostStatusRequest();

      setupMock(
          host,
          false,
          "task-id",
          AddCloudHostWorkflowService.State.class,
          null,
          null);
      target.getStatus(request.getOperation_id());
    }

    @Test
    public void getStatusWithTaskFailure() throws Throwable {
      ProvisionHostStatusRequest request = createProvisionHostStatusRequest();

      AddCloudHostWorkflowService.State returnedDocument = new AddCloudHostWorkflowService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = new TaskState();
      returnedDocument.taskState.stage = TaskState.TaskStage.FAILED;

      setupMock(
          host,
          true,
          "task-id",
          AddCloudHostWorkflowService.State.class,
          null,
          returnedDocument);

      ProvisionHostStatus status = target.getStatus(request.getOperation_id());
      assertThat(status.getResult(), is(ProvisionHostStatusCode.FAILED));
      Assert.assertNotNull(status.getError());
    }
  }

  private <T extends ServiceDocument> void setupMock(
      DeployerDcpServiceHost host,
      boolean isSuccess,
      final String operationUri,
      final Class<T> documentType,
      final Predicate<T> assertion,
      final T returnedDocument) {

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

          if (returnedDocument != null) {
            op.setBody(returnedDocument);
          }
          op.complete();
          return null;
        }
      }).when(host).sendRequest(argThat(opMatcher));
    } else {
      doThrow((new RuntimeException())).when(host).sendRequest(argThat(opMatcher));
    }
  }

  private ProvisionHostRequest createProvisionHostRequest() {
    ProvisionHostRequest request = new ProvisionHostRequest();
    request.setHost_id("host-id");
    return request;
  }

  private ProvisionHostStatusRequest createProvisionHostStatusRequest() {
    ProvisionHostStatusRequest request = new ProvisionHostStatusRequest();
    request.setOperation_id("task-id");
    return request;
  }
}
