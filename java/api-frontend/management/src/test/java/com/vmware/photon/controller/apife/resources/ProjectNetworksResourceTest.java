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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.virtualnetwork.ProjectNetworksResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.virtualnetwork.ProjectNetworksResource}.
 */
public class ProjectNetworksResourceTest extends ResourceTest {

  private String projectId = "projectId";
  private String projectNetworksRoutePath =
      UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_SUBNETS_PATH).build(projectId).toString();

  private String taskId = "task1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VirtualNetworkFeClient frontendClient;

  private PaginationConfig paginationConfig = new PaginationConfig();

  private VirtualNetworkCreateSpec spec;

  @Override
  public void setUpResources() throws Exception {
    spec = new VirtualNetworkCreateSpec();
    spec.setName("virtualNetworkName");
    spec.setDescription("virtualNetworkDescription");
    spec.setRoutingType(RoutingType.ROUTED);
    spec.setSize(8);
    spec.setReservedStaticIpSize(4);

    addResource(new ProjectNetworksResource(frontendClient, paginationConfig));
  }

  @BeforeMethod
  public void setUpTest() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
  }

  @Test
  public void succeedsToCreate() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(frontendClient).create(eq(projectId), eq(Project.KIND), refEq((spec)));

    Response response = createNetwork();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void failsToCreateWithException() throws Throwable {
    when(frontendClient.create(projectId, Project.KIND, spec)).thenThrow(new ExternalException("failed"));
    assertThat(createNetwork().getStatus(), is(500));
  }

  @Test
  public void failsToCreateWithInvalidSpec() throws Throwable {
    spec.setName(" bad name");
    assertThat(createNetwork().getStatus(), is(400));
  }

  @Test
  public void succeedsToListAll() throws Throwable {
    int virtualNetworkNumber = 2;
    List<VirtualNetwork> expectedVirtualNetworks = new ArrayList<>();
    for (int i = 0; i < virtualNetworkNumber; ++i) {
      VirtualNetwork expectedVirtualNetwork = new VirtualNetwork();
      expectedVirtualNetwork.setId(UUID.randomUUID().toString());
      expectedVirtualNetwork.setName("virtualNetwork" + i);

      expectedVirtualNetworks.add(expectedVirtualNetwork);
    }

    when(frontendClient.list(projectId, Project.KIND, Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(expectedVirtualNetworks));

    Response response = listNetworks(Optional.absent(), Optional.of(1), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualNetwork> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualNetwork>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(virtualNetworkNumber));
    for (int i = 0; i < virtualNetworkNumber; ++i) {
      VirtualNetwork expectedVirtualNetwork = expectedVirtualNetworks.get(i);
      VirtualNetwork actualVirtualNetwork = virtualNetworks.getItems().get(i);
      assertThat(actualVirtualNetwork, is(expectedVirtualNetwork));

      String apiRoutePath = UriBuilder
          .fromPath(SubnetResourceRoutes.SUBNET_PATH)
          .build(expectedVirtualNetwork.getId())
          .toString();
      assertThat(actualVirtualNetwork.getSelfLink().endsWith(apiRoutePath), is(true));
      assertThat(new URI(actualVirtualNetwork.getSelfLink()).isAbsolute(), is(true));
    }
  }

  @Test(dataProvider = "listAllWithPageSize")
  public void succeedsToListALlWithPageSize(Optional<Integer> pageSize,
                                            List<VirtualNetwork> expectedVirtualNetworks) throws Throwable {
    when(frontendClient.list(projectId, Project.KIND, Optional.absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(expectedVirtualNetworks, null, null));
    if (!expectedVirtualNetworks.isEmpty()) {
      when(frontendClient.list(projectId, Project.KIND, Optional.absent(), Optional.of(1)))
          .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualNetworks.get(0)),
              UUID.randomUUID().toString(), null));
    }
    when(frontendClient.list(projectId, Project.KIND, Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(expectedVirtualNetworks, null, null));
    when(frontendClient.list(projectId, Project.KIND, Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = listNetworks(Optional.absent(), pageSize, Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualNetwork> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualNetwork>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(expectedVirtualNetworks.size()));
    for (int i = 0; i < virtualNetworks.getItems().size(); ++i) {
      VirtualNetwork expectedVirtualNetwork = expectedVirtualNetworks.get(i);
      VirtualNetwork actualVirtualNetwork = virtualNetworks.getItems().get(i);
      assertThat(actualVirtualNetwork, is(expectedVirtualNetwork));

      String apiRoutePath = UriBuilder
          .fromPath(SubnetResourceRoutes.SUBNET_PATH)
          .build(expectedVirtualNetwork.getId())
          .toString();
      assertThat(actualVirtualNetwork.getSelfLink().endsWith(apiRoutePath), is(true));
      assertThat(new URI(actualVirtualNetwork.getSelfLink()).isAbsolute(), is(true));
    }
  }

  @DataProvider(name = "listAllWithPageSize")
  private Object[][] getListAllWithPageSize() {
    int virtualNetworkNumber = 2;
    List<VirtualNetwork> expectedVirtualNetworks = new ArrayList<>();
    for (int i = 0; i < virtualNetworkNumber; ++i) {
      VirtualNetwork expectedVirtualNetwork = new VirtualNetwork();
      expectedVirtualNetwork.setId(UUID.randomUUID().toString());
      expectedVirtualNetwork.setName("virtualNetwork" + i);

      expectedVirtualNetworks.add(expectedVirtualNetwork);
    }

    return new Object[][]{
        {
            Optional.absent(),
            expectedVirtualNetworks
        },
        {
            Optional.of(1),
            ImmutableList.of(expectedVirtualNetworks.get(0))
        },
        {
            Optional.of(2),
            expectedVirtualNetworks
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }

  @Test
  public void succeedsToListAllWithPageLink() throws Throwable {
    String pageLink = "randomPageLink";
    VirtualNetwork expectedVirtualNetwork = new VirtualNetwork();
    expectedVirtualNetwork.setId(UUID.randomUUID().toString());
    expectedVirtualNetwork.setName("virtualNetwork");

    when(frontendClient.nextList(pageLink))
        .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualNetwork)));

    Response response = listNetworks(Optional.absent(), Optional.absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualNetwork> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualNetwork>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(1));

    VirtualNetwork actualVirtualNetwork = virtualNetworks.getItems().get(0);
    assertThat(actualVirtualNetwork, is(expectedVirtualNetwork));

    String apiRoutePath = UriBuilder
        .fromPath(SubnetResourceRoutes.SUBNET_PATH)
        .build(expectedVirtualNetwork.getId())
        .toString();
    assertThat(actualVirtualNetwork.getSelfLink().endsWith(apiRoutePath), is(true));
    assertThat(new URI(actualVirtualNetwork.getSelfLink()).isAbsolute(), is(true));
  }

  @Test
  public void succeedsToListByName() throws Throwable {
    VirtualNetwork expectedVirtualNetwork = new VirtualNetwork();
    expectedVirtualNetwork.setId(UUID.randomUUID().toString());
    expectedVirtualNetwork.setName("virtualNetwork");

    when(frontendClient.list(projectId, Project.KIND, Optional.of("virtualNetwork"), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualNetwork)));

    Response response = listNetworks(Optional.of("virtualNetwork"), Optional.of(1), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualNetwork> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualNetwork>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(1));

    VirtualNetwork actualVirtualNetwork = virtualNetworks.getItems().get(0);
    assertThat(actualVirtualNetwork, is(expectedVirtualNetwork));

    String apiRoutePath = UriBuilder
        .fromPath(SubnetResourceRoutes.SUBNET_PATH)
        .build(expectedVirtualNetwork.getId())
        .toString();
    assertThat(actualVirtualNetwork.getSelfLink().endsWith(apiRoutePath), is(true));
    assertThat(new URI(actualVirtualNetwork.getSelfLink()).isAbsolute(), is(true));
  }

  @Test
  public void failsToListAllWithException() throws Throwable {
    when(frontendClient.list(projectId, Project.KIND, Optional.absent(), Optional.of(1)))
        .thenThrow(new ExternalException("failed"));
    assertThat(listNetworks(Optional.absent(), Optional.of(1), Optional.absent()).getStatus(),
        is(500));
  }

  @Test
  public void failsToListAllWithInvalidPageSize() throws Throwable {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = listNetworks(Optional.absent(), Optional.of(pageSize), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void failsToListAllWithInvalidPageLink() throws Throwable {
    String pageLink = "randomPageLink";
    doThrow(new PageExpiredException(pageLink)).when(frontendClient).nextList(pageLink);

    Response response = listNetworks(Optional.absent(), Optional.absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  @Test
  public void failsToListByNameWithException() throws Throwable {
    when(frontendClient.list(projectId, Project.KIND, Optional.of("virtualNetwork"), Optional.of(1)))
        .thenThrow(new ExternalException("failed"));
    assertThat(listNetworks(Optional.of("virtualNetwork"), Optional.of(1), Optional.absent()).getStatus(),
        is(500));
  }

  private Response createNetwork() {
    return client()
        .target(projectNetworksRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response listNetworks(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(projectNetworksRoutePath);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }
}
