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

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.apife.resources.vm.VmIsoDetachResource;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.vm.VmIsoDetachResource}.
 */
public class VmIsoDetachResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmDetachIsoRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_DETACH_ISO_PATH).build(vmId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VmFeClient vmFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VmIsoDetachResource(vmFeClient));
  }

  @Test
  public void testDetachIso() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.detachIso(eq(vmId))).thenReturn(task);

    Response response = client()
        .target(vmDetachIsoRoute)
        .request()
        .post(Entity.json(null));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }
}
