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

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.NetworkFeClient;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Tests {@link NetworksResource}.
 */
public class NetworksResourceTest extends ResourceTest {

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private NetworkFeClient networkFeClient;

  private NetworkCreateSpec spec;

  @Override
  public void setUpResources() throws Exception {
    spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    spec.setPortGroups(ImmutableList.of("PG1", "PG2"));

    addResource(new NetworksResource(networkFeClient));
  }

  @Test
  public void testSuccessfulCreateNetwork() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(networkFeClient.create(spec)).thenReturn(task);

    Response response = createNetwork();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
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

  @Test
  public void testGetAllNetworks() throws Exception {
    when(networkFeClient.find(Optional.<String>absent()))
        .thenReturn(new ResourceList<Network>(ImmutableList.of(createNetwork("n1"), createNetwork("n2"))));

    ResourceList<Network> networks = getNetworks(Optional.<String>absent());
    assertThat(networks.getItems().size(), is(2));
    assertThat(networks.getItems().get(0).getName(), is("n1"));
    assertThat(networks.getItems().get(1).getName(), is("n2"));

    for (int i = 0; i < networks.getItems().size(); i++) {
      Network network = networks.getItems().get(i);
      assertThat(new URI(network.getSelfLink()).isAbsolute(), is(true));
      assertThat(network.getSelfLink().endsWith(UriBuilder.fromPath(NetworkResourceRoutes.NETWORK_PATH)
          .build(network.getId()).toString()), is(true));
    }
  }

  @Test
  public void testGetNetworksByName() throws Exception {
    when(networkFeClient.find(Optional.of("n1")))
        .thenReturn(new ResourceList<>(ImmutableList.of(createNetwork("n1"))));

    ResourceList<Network> networks = getNetworks(Optional.of("n1"));
    assertThat(networks.getItems().size(), is(1));
    assertThat(networks.getItems().get(0).getName(), is("n1"));
  }

  private Response createNetwork() {
    return client()
        .target(NetworkResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private ResourceList<Network> getNetworks(Optional<String> name) {
    WebTarget resource = client().target(NetworkResourceRoutes.API);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get(new GenericType<ResourceList<Network>>() {
    });
  }

  private Network createNetwork(String name) {
    Network network = new Network();
    network.setId(UUID.randomUUID().toString());
    network.setName(name);
    network.setPortGroups(ImmutableList.of("PG1"));
    return network;
  }
}
