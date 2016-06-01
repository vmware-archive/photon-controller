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

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.clients.NetworkFeClient;
import com.vmware.photon.controller.apife.resources.physicalnetwork.NetworkDefaultSetResource;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.physicalnetwork.NetworkDefaultSetResource}.
 */
public class NetworkDefaultSetResourceTest extends ResourceTest {

  private String networkId = "network1";

  private String networkSetDefaultRoute =
      UriBuilder.fromPath(NetworkResourceRoutes.NETWORK_SET_DEFAULT_PATH).build(networkId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private NetworkFeClient networkFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new NetworkDefaultSetResource(networkFeClient));
  }

  @Test
  public void testSuccess() throws Throwable {
    Task task = new Task();
    task.setId(taskId);

    when(networkFeClient.setDefault(networkId)).thenReturn(task);

    Response response = client()
        .target(networkSetDefaultRoute)
        .request()
        .post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
