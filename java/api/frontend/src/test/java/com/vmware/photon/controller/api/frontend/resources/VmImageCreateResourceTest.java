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

import com.vmware.photon.controller.api.frontend.clients.VmFeClient;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.vm.VmImageCreateResource;
import com.vmware.photon.controller.api.model.ImageCreateSpec;
import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.Task;

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
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.VmImageCreateResource}.
 */
public class VmImageCreateResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmCreateImageRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_CREATE_IMAGE_PATH).build(vmId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VmFeClient vmFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VmImageCreateResource(vmFeClient));
  }

  @Test
  public void testCreateImage() throws Throwable {
    Task task = new Task();
    task.setId(taskId);

    ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
    imageCreateSpec.setName("image1");
    imageCreateSpec.setReplicationType(ImageReplicationType.EAGER);

    when(vmFeClient.createImage(vmId, imageCreateSpec)).thenReturn(task);

    Response response = client()
        .target(vmCreateImageRoute)
        .request()
        .post(Entity.entity(imageCreateSpec, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
