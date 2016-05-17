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

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Tests {@link VirtualNetworkFeClient}.
 */
public class VirtualNetworkFeClientTest {

  private ObjectMapper objectMapper;
  private HousekeeperXenonRestClient backendClient;
  private ApiFeXenonRestClient cloudStoreClient;
  private VirtualNetworkFeClient frontendClient;

  @BeforeMethod
  public void setUp() {
    objectMapper = new ObjectMapper();

    backendClient = mock(HousekeeperXenonRestClient.class);
    doNothing().when(backendClient).start();

    cloudStoreClient = mock(ApiFeXenonRestClient.class);
    doNothing().when(cloudStoreClient).start();

    frontendClient = new VirtualNetworkFeClient(backendClient, cloudStoreClient);
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
    expectedStartState.parentId = "parentId";
    expectedStartState.parentKind = "parentKind";

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

    Task task = frontendClient.create("parentId", "parentKind", spec);

    verify(backendClient).post(any(String.class), any(CreateVirtualNetworkWorkflowDocument.class));
    assertThat(task.getState(), is(TaskService.State.TaskState.COMPLETED.toString()));
  }

  @Test
  public void succeedsToDelete() throws Throwable {
    DeleteVirtualNetworkWorkflowDocument expectedFinalState = new DeleteVirtualNetworkWorkflowDocument();
    expectedFinalState.taskServiceState = new TaskService.State();
    expectedFinalState.taskServiceState.state = TaskService.State.TaskState.COMPLETED;
    expectedFinalState.taskServiceState.documentSelfLink = UUID.randomUUID().toString();

    Operation operation = new Operation();
    operation.setBody(expectedFinalState);

    String networkId = UUID.randomUUID().toString();
    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.virtualNetworkId = networkId;

    doReturn(operation).when(backendClient).post(
        eq(DeleteVirtualNetworkWorkflowService.FACTORY_LINK),
        refEq(startState)
    );

    Task task = frontendClient.delete(networkId);

    verify(backendClient).post(eq(DeleteVirtualNetworkWorkflowService.FACTORY_LINK), refEq(startState));
    assertThat(task.getState(), is(TaskService.State.TaskState.COMPLETED.toString()));
  }

  @Test
  public void succeedsToGet() throws Throwable {
    VirtualNetworkService.State virtualNetworkState = new VirtualNetworkService.State();
    virtualNetworkState.name = "virtualNetwork";

    Operation operation = new Operation();
    operation.setBody(virtualNetworkState);

    String networkId = UUID.randomUUID().toString();

    doReturn(operation).when(cloudStoreClient).get(
        eq(VirtualNetworkService.FACTORY_LINK + "/" + networkId));


    VirtualNetwork virtualNetwork = frontendClient.get(networkId);
    assertEquals(virtualNetwork.getName(), virtualNetworkState.name);
  }

  @Test(expectedExceptions = NetworkNotFoundException.class)
  public void failsToGetWithException() throws Throwable {
    doThrow(new DocumentNotFoundException(new Operation(), null))
        .when(cloudStoreClient).get(anyString());

    frontendClient.get("networkId");
  }

  @Test(dataProvider = "listAllTestData")
  public void succeedsToListAll(String parentId,
                                String parentKind,
                                Optional<String> name,
                                ImmutableMap<String, String> terms) throws Throwable {
    VirtualNetworkService.State expectedVirtualNetworkState = new VirtualNetworkService.State();
    expectedVirtualNetworkState.state = NetworkState.READY;

    ServiceDocumentQueryResult expectedQueryResult = new ServiceDocumentQueryResult();
    String documentLink = UUID.randomUUID().toString();
    String nextPageLink = "nextPageLink" + UUID.randomUUID().toString();
    String prevPageLink = "prevPageLink" + UUID.randomUUID().toString();
    expectedQueryResult.documentLinks = new ArrayList<>();
    expectedQueryResult.documentLinks.add(documentLink);
    expectedQueryResult.documents = new HashMap<>();
    expectedQueryResult.documents.put(documentLink, objectMapper.writeValueAsString(expectedVirtualNetworkState));
    expectedQueryResult.nextPageLink = nextPageLink;
    expectedQueryResult.prevPageLink = prevPageLink;

    doReturn(expectedQueryResult).when(cloudStoreClient).queryDocuments(
        eq(VirtualNetworkService.State.class),
        refEq(terms),
        eq(Optional.absent()),
        eq(true));

    ResourceList<VirtualNetwork> actualVirtualNetworks = frontendClient.list(
        parentId,
        parentKind,
        name,
        Optional.absent());

    verify(cloudStoreClient).queryDocuments(
        eq(VirtualNetworkService.State.class),
        refEq(terms),
        eq(Optional.absent()),
        eq(true));
    assertThat(actualVirtualNetworks.getItems().size(), is(1));

    VirtualNetwork actualVirtualNetwork = actualVirtualNetworks.getItems().get(0);
    assertThat(actualVirtualNetwork.getState(), is(NetworkState.READY));
  }

  @DataProvider(name = "listAllTestData")
  private Object[][] getListAllTestData() {
    return new Object[][] {
        // parentId, parentKind, name, expectedTerms
        {
            null,
            null,
            Optional.absent(),
            ImmutableMap.of()
        },
        {
            "parentId",
            "parentKind",
            Optional.absent(),
            ImmutableMap.of("parentId", "parentId", "parentKind", "parentKind")
        },
        {
            null,
            null,
            Optional.of("name"),
            ImmutableMap.of("name", "name")
        },
        {
            "parentId",
            "parentKind",
            Optional.of("name"),
            ImmutableMap.of("parentId", "parentId", "parentKind", "parentKind", "name", "name")
        }
    };
  }
}
