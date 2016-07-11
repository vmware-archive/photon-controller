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

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.vm.ProjectVmsResource;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpStatus;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link com.vmware.photon.controller.apife.resources.vm.ProjectVmsResource}.
 * In this test, we only test virtual network related stuff. Other test cases are in
 * {@link com.vmware.photon.controller.apife.resources.ProjectVmsResourceTest}. The reason
 * of putting them in different files is that we cannot dynamically create the ProjectVmsResource
 * in the same file.
 */
public class VmOnVirtualNetworkTest extends ResourceTest {
  private String projectId = "p1";
  private String projectVmsRoutePath =
      UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_VMS_PATH).build(projectId).toString();
  private String taskId = "task1";
  private String taskRoutePath = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private PaginationConfig paginationConfig = new PaginationConfig();
  private VmCreateSpec spec;

  @Mock
  private VmFeClient vmFeClient;

  @Override
  protected void setUpResources() throws Exception {
    spec = new VmCreateSpec();
    spec.setName("vm0");
    spec.setSourceImageId("x");

    List<AttachedDiskCreateSpec> disks = new ArrayList<>();
    disks.add(new AttachedDiskCreateSpecBuilder().name("name").flavor("flavor").bootDisk(true).build());
    spec.setAttachedDisks(disks);

    addResource(new ProjectVmsResource(vmFeClient, paginationConfig, true));
  }

  @Test
  public void testSuccessfulCreate() throws Exception {
    List<String> networks = ImmutableList.of("network1");
    spec.setSubnets(networks);

    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(vmFeClient).create(projectId, spec);

    Response response = createVm();
    assertThat(response.getStatus(), is(HttpStatus.SC_CREATED));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  private Response createVm() {
    return client()
        .target(projectVmsRoutePath)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }
}
