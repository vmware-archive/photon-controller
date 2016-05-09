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

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.virtualnetwork.NetworksResource;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.virtualnetwork.NetworksResource}.
 */
public class VirtualNetworksResourceTest extends ResourceTest {

  private String taskId = "task1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VirtualNetworkFeClient frontendClient;

  private VirtualNetworkCreateSpec spec;

  @Override
  public void setUpResources() throws Exception {
    spec = new VirtualNetworkCreateSpec();
    spec.setName("virtualNetworkName");
    spec.setDescription("virtualNetworkDescription");
    spec.setRoutingType(RoutingType.ROUTED);

    addResource(new NetworksResource(frontendClient));
  }

  @Test
  public void succeedsToCreate() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(frontendClient).create(refEq((spec)));

    Response response = createNetwork();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void failsToCreateWithException() throws Throwable {
    when(frontendClient.create(spec)).thenThrow(new ExternalException("failed"));
    assertThat(createNetwork().getStatus(), is(500));
  }

  @Test
  public void failsToCreateWithInvalidSpec() throws Throwable {
    spec.setName(" bad name");
    assertThat(createNetwork().getStatus(), is(400));
  }

  private Response createNetwork() {
    return client()
        .target(NetworkResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }
}
