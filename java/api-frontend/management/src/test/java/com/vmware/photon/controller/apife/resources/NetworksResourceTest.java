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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.NetworkFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.physicalnetwork.NetworksResource;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.physicalnetwork.NetworksResource}.
 */
public class NetworksResourceTest extends ResourceTest {

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private NetworkFeClient networkFeClient;

  private NetworkCreateSpec spec;
  private PaginationConfig paginationConfig = new PaginationConfig();
  private Network n1 = createNetwork("n1");
  private Network n2 = createNetwork("n2");

  @Override
  public void setUpResources() throws Exception {
    spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    spec.setPortGroups(ImmutableList.of("PG1", "PG2"));

    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    addResource(new NetworksResource(networkFeClient, paginationConfig));
  }

  @Test
  public void testSuccessfulCreateNetwork() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(networkFeClient.create(spec)).thenReturn(task);

    Response response = createNetwork();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testFailedCreateNetwork() throws Exception {
    when(networkFeClient.create(spec)).thenThrow(new ExternalException("failed"));
    assertThat(createNetwork().getStatus(), is(500));
  }

  @Test
  public void testInvalidNetwork() throws Exception {
    spec.setName(" bad name");
    assertThat(createNetwork().getStatus(), is(400));
  }

  @Test
  public void createInvalidJsonNetwork() {
    Response r = client()
        .target(NetworkResourceRoutes.API)
        .request()
        .post(Entity.entity("{ \"name\":\"thename\",\"foo\"}", MediaType.APPLICATION_JSON_TYPE));
    assertThat(r.getStatus(), is(400));
  }

  @Test(dataProvider = "pageSizes")
  public void testGetNetworks(Optional<Integer> pageSize, List<Network> expectedNetworks) throws Exception {
    doReturn(new ResourceList<>(ImmutableList.of(n1, n2)))
        .when(networkFeClient)
        .find(Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(ImmutableList.of(n1), UUID.randomUUID().toString(), null))
        .when(networkFeClient)
        .find(Optional.<String>absent(), Optional.of(1));
    doReturn(new ResourceList<>(ImmutableList.of(n1, n2)))
        .when(networkFeClient)
        .find(Optional.<String>absent(), Optional.of(2));
    doReturn(new ResourceList<>(Collections.emptyList()))
        .when(networkFeClient)
        .find(Optional.<String>absent(), Optional.of(3));

    Response response = getNetworks(Optional.<String>absent(), pageSize, Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Network> networks = response.readEntity(new GenericType<ResourceList<Network>>() {
    });
    assertThat(networks.getItems().size(), is(expectedNetworks.size()));

    for (int i = 0; i < networks.getItems().size(); i++) {
      Network network = networks.getItems().get(i);
      assertThat(network, is(expectedNetworks.get(i)));
      assertThat(new URI(network.getSelfLink()).isAbsolute(), is(true));
      assertThat(network.getSelfLink().endsWith(UriBuilder.fromPath(NetworkResourceRoutes.NETWORK_PATH)
          .build(network.getId()).toString()), is(true));
    }
  }

  @Test
  public void testInvalidPageSize() throws ExternalException {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getNetworks(Optional.<String>absent(), Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testGetNetworksByName() throws Exception {
    when(networkFeClient.find(Optional.of("n1"), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(n1)));

    Response response = getNetworks(Optional.of("n1"), Optional.<Integer>absent(), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Network> networks = response.readEntity(new GenericType<ResourceList<Network>>() {
    });

    assertThat(networks.getItems().size(), is(1));
    assertThat(networks.getItems().get(0), is(n1));
  }

  @Test
  public void testGetNetworksPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(n1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(networkFeClient).getPage(pageLink);

    Response response = getNetworks(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Network> networks = response.readEntity(new GenericType<ResourceList<Network>>() {
    });
    assertThat(networks.getItems().size(), is(1));

    Network network = networks.getItems().get(0);
    assertThat(network, is(n1));
    assertThat(new URI(network.getSelfLink()).isAbsolute(), is(true));
    assertThat(network.getSelfLink().endsWith(UriBuilder.fromPath(NetworkResourceRoutes.NETWORK_PATH)
        .build(network.getId()).toString()), is(true));
  }

  @Test
  public void testInvalidClustersPageLink() throws ExternalException {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(networkFeClient).getPage(pageLink);

    Response response = getNetworks(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  private Response createNetwork() {
    return client()
        .target(NetworkResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getNetworks(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(NetworkResourceRoutes.API);
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

  private Network createNetwork(String name) {
    Network network = new Network();
    network.setId(UUID.randomUUID().toString());
    network.setName(name);
    network.setPortGroups(ImmutableList.of("PG1"));
    return network;
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][]{
        {
            Optional.absent(),
            ImmutableList.of(n1, n2)
        },
        {
            Optional.of(1),
            ImmutableList.of(n1)
        },
        {
            Optional.of(2),
            ImmutableList.of(n1, n2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
