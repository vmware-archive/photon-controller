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

import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowService;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatus;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;

/**
 * This class tests the {@link DeprovisionHostWorkflowServiceClient}.
 */
public class DeprovisionHostWorkflowServiceClientTest {

  @Test
  private void dummy() {
  }

  /**
   * This class tests the deprovision method.
   */
  public class CreateDeprovisionHostWorkflowEntity {

    private DeprovisionHostWorkflowServiceClient target;
    private DeployerXenonServiceHost host;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      target = new DeprovisionHostWorkflowServiceClient(host);
    }

    @Test
    public void successCreate() throws Throwable {
      DeprovisionHostRequest request = createDeprovisionHostRequest();

      DeprovisionHostWorkflowService.State returnedDocument = new DeprovisionHostWorkflowService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.documentSelfLink = "task-id";

      setupMock(
          host,
          true,
          DeprovisionHostWorkflowFactoryService.SELF_LINK,
          DeprovisionHostWorkflowService.State.class,
          (state) -> {
            assertThat(state.hostServiceLink, is("host-id"));
            return true;
          },
          returnedDocument);

      String taskLink = target.deprovision(request.getHost_id());
      assertThat(taskLink, is("task-id"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsCreateWhenDcpHostThrowsException() throws Throwable {
      DeprovisionHostRequest request = createDeprovisionHostRequest();

      setupMock(
          host,
          false,
          DeprovisionHostWorkflowFactoryService.SELF_LINK,
          DeprovisionHostWorkflowService.State.class,
          null,
          null);
      target.deprovision(request.getHost_id());
    }
  }

  /**
   * This class tests the getStatus method.
   */
  public class GetStatusDeprovisionHost {

    private DeprovisionHostWorkflowServiceClient target;
    private DeployerXenonServiceHost host;

    @BeforeMethod
    public void before() {
      host = mock(DeployerXenonServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      target = new DeprovisionHostWorkflowServiceClient(host);
    }

    @Test
    public void successGetStatus() throws Throwable {
      DeprovisionHostStatusRequest request = createDeprovisionHostStatusRequest();

      DeprovisionHostWorkflowService.State returnedDocument = new DeprovisionHostWorkflowService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = new DeprovisionHostWorkflowService.TaskState();
      returnedDocument.taskState.stage = TaskState.TaskStage.FINISHED;

      setupMock(
          host,
          true,
          "task-id",
          DeprovisionHostWorkflowService.State.class,
          null,
          returnedDocument);

      DeprovisionHostStatus status = target.getStatus(request.getOperation_id());
      assertThat(status.getResult(), is(DeprovisionHostStatusCode.FINISHED));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsGetStatusWhenDcpHostThrowsException() throws Throwable {
      DeprovisionHostStatusRequest request = createDeprovisionHostStatusRequest();

      setupMock(
          host,
          false,
          "task-id",
          DeprovisionHostWorkflowService.State.class,
          null,
          null);
      target.getStatus(request.getOperation_id());
    }

    @Test
    public void getStatusWithTaskFailure() throws Throwable {
      DeprovisionHostStatusRequest request = createDeprovisionHostStatusRequest();

      DeprovisionHostWorkflowService.State returnedDocument = new DeprovisionHostWorkflowService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = new DeprovisionHostWorkflowService.TaskState();
      returnedDocument.taskState.stage = TaskState.TaskStage.FAILED;

      setupMock(
          host,
          true,
          "task-id",
          DeprovisionHostWorkflowService.State.class,
          null,
          returnedDocument);

      DeprovisionHostStatus status = target.getStatus(request.getOperation_id());
      assertThat(status.getResult(), is(DeprovisionHostStatusCode.FAILED));
      Assert.assertNotNull(status.getError());
    }
  }

  private <T extends ServiceDocument> void setupMock(
      DeployerXenonServiceHost host,
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

  private DeprovisionHostRequest createDeprovisionHostRequest() {
    DeprovisionHostRequest request = new DeprovisionHostRequest();
    request.setHost_id("host-id");
    return request;
  }

  private DeprovisionHostStatusRequest createDeprovisionHostStatusRequest() {
    DeprovisionHostStatusRequest request = new DeprovisionHostStatusRequest();
    request.setOperation_id("task-id");
    return request;
  }
}
