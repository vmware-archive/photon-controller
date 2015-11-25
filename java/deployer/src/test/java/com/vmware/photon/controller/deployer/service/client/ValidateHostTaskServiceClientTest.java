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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.photon.controller.deployer.gen.CreateHostRequest;
import com.vmware.photon.controller.resource.gen.Host;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Implements tests for {@link ValidateHostTaskServiceClient}.
 */
public class ValidateHostTaskServiceClientTest {

  @Test
  public void dummy() {
  }

  /**
   * This class tests the change host mode method.
   */
  public class CreateValidateHostTaskEntity {

    private ValidateHostTaskServiceClient target;
    private DeployerDcpServiceHost host;


    @BeforeMethod
    public void before() {
      host = mock(DeployerDcpServiceHost.class);
      target = new ValidateHostTaskServiceClient(host);
    }

    @Test
    public void successValidateHost() throws Throwable {
      CreateHostRequest request = createCreateHostRequest();

      ValidateHostTaskService.State returnedDocument = new ValidateHostTaskService.State();
      returnedDocument.hostAddress = "host-addr";
      returnedDocument.documentSelfLink = "task-id";

      setupMock(
          host,
          true,
          ValidateHostTaskFactoryService.SELF_LINK,
          ValidateHostTaskService.State.class,
          (state) -> {
            assertThat(state.hostAddress, is("host-addr"));
            return true;
          },
          returnedDocument);

      String taskLink = target.validate(request.getHost());
      assertThat(taskLink, is("task-id"));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsValidateHostWhenDcpHostThrowsException() throws Throwable {
      CreateHostRequest request = createCreateHostRequest();

      setupMock(
          host,
          false,
          ValidateHostTaskFactoryService.SELF_LINK,
          ValidateHostTaskService.State.class,
          null,
          null);

      target.validate(request.getHost());
    }
  }

  /**
   * This class tests the Get Change Host Mode Status method.
   */
  public class GetValidateHostStatus {

    private ValidateHostTaskServiceClient target;
    private DeployerDcpServiceHost host;

    @BeforeMethod
    public void before() {
      host = mock(DeployerDcpServiceHost.class);
      target = new ValidateHostTaskServiceClient(host);
    }

    @Test
    public void successGetValidateHostStatus() throws Throwable {

      ValidateHostTaskService.TaskState returnedState = new ValidateHostTaskService.TaskState();
      returnedState.stage = TaskState.TaskStage.FINISHED;

      ValidateHostTaskService.State returnedDocument = new ValidateHostTaskService.State();
      returnedDocument.hostAddress = "host-addr";
      returnedDocument.documentSelfLink = "task-id";
      returnedDocument.taskState = returnedState;

      setupMock(
          host,
          true,
          "operationId",
          ValidateHostTaskService.State.class,
          (state) -> {
            assertThat(state.hostAddress, is("host-addr"));
            return true;
          },
          returnedDocument);

      TaskState state = target.getValidateHostStatus("operationId");
      assertThat(state.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test(expectedExceptions = Exception.class)
    public void failsGetValidateHostStatusWhenDcpHostThrowsException() throws Throwable {

      setupMock(
          host,
          false,
          ValidateHostTaskFactoryService.SELF_LINK,
          ValidateHostTaskService.State.class,
          null,
          null);

      target.getValidateHostStatus("operationId");
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

  private CreateHostRequest createCreateHostRequest() {
    CreateHostRequest request = new CreateHostRequest();
    Host host = createHost();
    request.setHost(host);
    return request;
  }

  private Host createHost() {
    Host host = new Host();
    host.setId("host-id");
    host.setAddress("host-addr");
    host.setUsername("username");
    host.setPassword("password");
    Map<String, String> metadata = new HashMap<>();
    metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "managementDatastore");
    metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "managementPortGroup");
    metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "managementNetworkIp");
    metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES, "datastore1, datastore2");
    metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS, "VM Network, Management VLan");
    host.setMetadata(metadata);
    host.setUsageTags(Collections.singleton("MGMT"));
    return host;
  }


}
