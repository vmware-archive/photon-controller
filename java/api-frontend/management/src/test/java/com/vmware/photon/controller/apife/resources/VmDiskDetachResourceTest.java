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

import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.apife.resources.vm.VmDiskDetachResource;
import com.vmware.photon.controller.resource.gen.Disk;

import org.hamcrest.Matchers;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.vm.VmDiskDetachResource}.
 */

public class VmDiskDetachResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmDetachDiskRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_DETACH_DISK_PATH).build(vmId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VmFeClient vmFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VmDiskDetachResource(vmFeClient));
  }

  @Test
  public void testDetachDisk() throws Exception {

    Disk disk = new Disk();
    disk.setId("disk1");
    List<String> diskIdList = new ArrayList<String>();
    diskIdList.add(disk.getId());

    VmDiskOperation vmDiskOperation = new VmDiskOperation();
    vmDiskOperation.setDiskId(disk.getId());

    Task task = new Task();
    task.setId("task1");

    when(vmFeClient.operateDisks("vm1", diskIdList, Operation.DETACH_DISK)).thenReturn(task);

    Response response = client()
        .target(vmDetachDiskRoute)
        .request()
        .post(Entity.entity(vmDiskOperation, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
