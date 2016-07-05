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
import com.vmware.photon.controller.api.Subnet;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.clients.NetworkFeClient;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.resources.physicalnetwork.SubnetResource;
import com.vmware.photon.controller.apife.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link SubnetResource}.
 */
public class SubnetResourceTest extends ResourceTest {

  private String networkId = "network-id";

  private String taskId = "task1";

  private String networkRoute =
      UriBuilder.fromPath(SubnetResourceRoutes.SUBNET_PATH).build(networkId).toString();

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  private String networkSetDefaultRoute =
      UriBuilder.fromPath(SubnetResourceRoutes.SUBNET_PATH)
          .path(SubnetResourceRoutes.SUBNET_SET_DEFAULT_ACTION)
          .build(networkId)
          .toString();

  @Mock
  private NetworkFeClient networkFeClient;

  @Override
  public void setUpResources() throws Exception {
    addResource(new SubnetResource(networkFeClient));
  }

  @Test
  public void testGetNetworkById() throws Exception {
    Subnet subnet = new Subnet();
    subnet.setId(networkId);
    subnet.setName("network1");
    subnet.setPortGroups(ImmutableList.of("PG1", "PG2"));

    when(networkFeClient.get(networkId)).thenReturn(subnet);

    Response response = client().target(networkRoute).request().get();
    assertThat(response.getStatus(), is(200));

    Subnet responseSubnet = response.readEntity(Subnet.class);
    assertThat(responseSubnet, is(subnet));
    assertThat(new URI(responseSubnet.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseSubnet.getSelfLink().endsWith(networkRoute), is(true));
  }

  @Test
  public void testSuccessfulDelete() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(networkFeClient.delete(networkId)).thenReturn(task);

    Response response = client()
        .target(networkRoute)
        .request()
        .delete();

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), Matchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), Matchers.is(true));
  }

  @Test
  public void testDeleteByInvalidId() throws Exception {
    when(networkFeClient.delete(networkId)).thenThrow(new NetworkNotFoundException(networkId));

    Response response = client()
        .target(networkRoute)
        .request()
        .delete();

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("NetworkNotFound"));
    assertThat(errors.getMessage(), containsString("Network " + networkId + " not found"));
  }

  @Test
  public void testSuccessfulSetDefault() throws Throwable {
    Task task = new Task();
    task.setId(taskId);

    when(networkFeClient.setDefault(networkId)).thenReturn(task);

    Response response = client()
        .target(networkSetDefaultRoute)
        .request()
        .post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), CoreMatchers.is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, CoreMatchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

}
