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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeRequest;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.function.Predicate;

/**
 * Implements tests for {@link ChangeHostModeTaskServiceClient}.
 */
public class ChangeHostModeTaskServiceClientTest {

  @Test
  public void dummy() {
  }

  /**
   * This class tests the change host mode method.
   */
  public class CreateChangeHostModeTaskEntity {

    private ChangeHostModeTaskServiceClient target;
    private DeployerDcpServiceHost host;


    @BeforeMethod
    public void before() {
      host = mock(DeployerDcpServiceHost.class);
      target = new ChangeHostModeTaskServiceClient(host);
    }

    @Test
    public void successChangeHostMode() throws Throwable {
      EnterMaintenanceModeRequest request = createEnterMaintenanceModeRequest();

      ChangeHostModeTaskService.State returnedDocument = new ChangeHostModeTaskService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.hostMode = HostMode.ENTERING_MAINTENANCE;
      returnedDocument.documentSelfLink = "task-id";

      setupMock(
          host,
          true,
          ChangeHostModeTaskFactoryService.SELF_LINK,
          ChangeHostModeTaskService.State.class,
          (state) -> {
            assertThat(state.hostServiceLink, is(HostServiceFactory.SELF_LINK + "/" + "host-id"));
            return true;
          },
          returnedDocument);

      String taskLink = target.changeHostMode(request.getHostId(), HostMode.ENTERING_MAINTENANCE);
      assertThat(taskLink, is("task-id"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsChangeHostModeWhenDcpHostThrowsException() throws Throwable {
      EnterMaintenanceModeRequest request = createEnterMaintenanceModeRequest();

      setupMock(
          host,
          false,
          ChangeHostModeTaskFactoryService.SELF_LINK,
          ChangeHostModeTaskService.State.class,
          null,
          null);

      target.changeHostMode(request.getHostId(), HostMode.ENTERING_MAINTENANCE);
    }
  }

  /**
   * This class tests the Get Change Host Mode Status method.
   */
  public class GetChangeHostModeStatus {

    private ChangeHostModeTaskServiceClient target;
    private DeployerDcpServiceHost host;

    @BeforeMethod
    public void before() {
      host = mock(DeployerDcpServiceHost.class);
      target = new ChangeHostModeTaskServiceClient(host);
    }

    @Test
    public void successGetChangeHostModeStatus() throws Throwable {

      TaskState returnedState = new TaskState();
      returnedState.stage = TaskState.TaskStage.FINISHED;

      ChangeHostModeTaskService.State returnedDocument = new ChangeHostModeTaskService.State();
      returnedDocument.hostServiceLink = "host-id";
      returnedDocument.hostMode = HostMode.ENTERING_MAINTENANCE;
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = returnedState;

      setupMock(
          host,
          true,
          "operationId",
          ChangeHostModeTaskService.State.class,
          (state) -> {
            assertThat(state.hostServiceLink, is(HostServiceFactory.SELF_LINK + "/" + "host-id"));
            return true;
          },
          returnedDocument);

      TaskState state = target.getChangeHostModeStatus("operationId");
      assertThat(state.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsGetChangeHostModeStatusWhenDcpHostThrowsException() throws Throwable {

      setupMock(
          host,
          false,
          ChangeHostModeTaskFactoryService.SELF_LINK,
          ChangeHostModeTaskService.State.class,
          null,
          null);

      target.getChangeHostModeStatus("operationId");
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
      doThrow(new RuntimeException()).when(host).sendRequest(argThat(opMatcher));
    }
  }

  private EnterMaintenanceModeRequest createEnterMaintenanceModeRequest() {
    EnterMaintenanceModeRequest request = new EnterMaintenanceModeRequest();
    request.setHostId("host-id");
    return request;
  }
}
