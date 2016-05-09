/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

/**
 * Tests {@link VirtualNetworkFeClient}.
 */
public class VirtualNetworkFeClientTest {

  private HousekeeperXenonRestClient backendClient;
  private VirtualNetworkFeClient frontendClient;

  @BeforeMethod
  public void setUp() {
    backendClient = mock(HousekeeperXenonRestClient.class);
    doNothing().when(backendClient).start();

    frontendClient = new VirtualNetworkFeClient(backendClient);
  }

  @Test
  public void succeedsToCreate() throws Throwable {
    VirtualNetworkCreateSpec spec = new VirtualNetworkCreateSpec();
    spec.setName("virtualNetworkName");
    spec.setDescription("virtualNetworkDescription");
    spec.setRoutingType(RoutingType.ROUTED);

    CreateVirtualNetworkWorkflowDocument expectedStartState = new CreateVirtualNetworkWorkflowDocument();
    expectedStartState.name = spec.getName();
    expectedStartState.description = spec.getDescription();
    expectedStartState.routingType = spec.getRoutingType();

    CreateVirtualNetworkWorkflowDocument expectedFinalState = new CreateVirtualNetworkWorkflowDocument();
    expectedFinalState.taskServiceState = new TaskService.State();
    expectedFinalState.taskServiceState.entityId = "entityId";
    expectedFinalState.taskServiceState.entityKind = "entityKind";
    expectedFinalState.taskServiceState.state = TaskService.State.TaskState.COMPLETED;
    expectedFinalState.taskServiceState.documentSelfLink =
        CreateVirtualNetworkWorkflowService.FACTORY_LINK + "/" + UUID.randomUUID().toString();

    Operation operation = new Operation();
    operation.setBody(expectedFinalState);

    doReturn(operation).when(backendClient).post(
        eq(CreateVirtualNetworkWorkflowService.FACTORY_LINK),
        refEq(expectedStartState));

    frontendClient.create(spec);

    verify(backendClient).post(any(String.class), any(CreateVirtualNetworkWorkflowDocument.class));
  }
}
