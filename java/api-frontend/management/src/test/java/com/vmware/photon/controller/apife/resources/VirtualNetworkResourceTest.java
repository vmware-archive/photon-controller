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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.virtualnetwork.NetworkResource;

import org.apache.http.HttpStatus;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.virtualnetwork.NetworkResource}.
 */
public class VirtualNetworkResourceTest extends ResourceTest {

  private String taskId = "task1";
  private String networkId = "network1";
  private String networkRoute = UriBuilder.fromPath(NetworkResourceRoutes.NETWORK_PATH).build(networkId).toString();
  private String taskRoutePath = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VirtualNetworkFeClient frontendClient;

  @Override
  public void setUpResources() throws Exception {
    addResource(new NetworkResource(frontendClient));
  }

  @Test
  public void testSuccessfulDelete() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(frontendClient).delete(networkId);

    Response response = client().target(networkRoute).request().delete();
    assertThat(response.getStatus(), is(HttpStatus.SC_CREATED));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testFailedToDelete() throws Exception {
    doThrow(new ExternalException("failed")).when(frontendClient).delete(networkId);

    Response response = client().target(networkRoute).request().delete();
    assertThat(response.getStatus(), is(HttpStatus.SC_INTERNAL_SERVER_ERROR));
  }
}
