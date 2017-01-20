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

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.clients.ServiceFeClient;
import com.vmware.photon.controller.api.frontend.resources.routes.ServiceResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.service.ServiceResizeResource;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.Task;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.service.ServiceResizeResource}.
 */
public class ServiceResizeResourceTest extends ResourceTest {

  private static final String serviceId = "serviceId1";
  private static final String taskId = "taskId1";

  private static final String serviceResizeRoute =
      UriBuilder.fromPath(ServiceResourceRoutes.SERVICES_RESIZE_PATH).build(serviceId).toString();
  private static final String taskRoute =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ServiceFeClient serviceFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ServiceResizeResource(serviceFeClient));
  }

  @Test
  public void testResizeService() throws Exception {
    ServiceResizeOperation op = new ServiceResizeOperation();
    op.setNewWorkerCount(100);

    Task t1 = new Task();
    t1.setId(taskId);
    when(serviceFeClient.resize(serviceId, op)).thenReturn(t1);

    Response response = client()
        .target(serviceResizeRoute)
        .request()
        .post(Entity.entity(op, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task t2 = response.readEntity(Task.class);
    assertThat(t2, is(t1));
    assertThat(new URI(t2.getSelfLink()).isAbsolute(), is(true));
    assertThat(t2.getSelfLink().endsWith(taskRoute), is(true));
  }
}
