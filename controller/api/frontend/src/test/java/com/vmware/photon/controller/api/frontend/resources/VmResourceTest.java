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
import com.vmware.photon.controller.api.frontend.resources.vm.VmMksTicketResource;
import com.vmware.photon.controller.api.frontend.resources.vm.VmNetworksResource;
import com.vmware.photon.controller.api.frontend.resources.vm.VmResource;
import com.vmware.photon.controller.api.frontend.resources.vm.VmTagsResource;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmOperation;

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

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.VmResource}.
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.VmMksTicketResource}.
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.VmNetworksResource}.
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.vm.VmTagsResource}.
 */
public class VmResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vmId).toString();

  private String vmTagsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_TAGS_PATH).build(vmId).toString();

  private String vmNetworksRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_SUBNETS_PATH).build(vmId).toString();

  private String vmMksTicketRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_MKS_TICKET_PATH).build(vmId).toString();

  private String vmStartOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH + VmResourceRoutes.VM_START_ACTION).build(vmId).toString();

  private String vmStopOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH + VmResourceRoutes.VM_STOP_ACTION).build(vmId).toString();

  private String vmRestartOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH + VmResourceRoutes.VM_RESTART_ACTION).build(vmId).toString();

  private String vmSuspendOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH + VmResourceRoutes.VM_SUSPEND_ACTION).build(vmId).toString();

  private String vmResumeOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH + VmResourceRoutes.VM_RESUME_ACTION).build(vmId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private VmFeClient vmFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VmResource(vmFeClient));
    addResource(new VmMksTicketResource(vmFeClient));
    addResource(new VmTagsResource(vmFeClient));
    addResource(new VmNetworksResource(vmFeClient));
  }

  @Test
  public void testGetVmById() throws Exception {
    Vm vm = new Vm();
    vm.setId(vmId);
    vm.setName("vm1name");

    when(vmFeClient.get(vmId)).thenReturn(vm);

    Response response = client().target(vmRoute).request().get();
    assertThat(response.getStatus(), is(200));

    Vm responseVm = response.readEntity(Vm.class);
    assertThat(responseVm, is(vm));
    assertThat(new URI(responseVm.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseVm.getSelfLink().endsWith(vmRoute), is(true));
  }

  @Test
  public void testAddTagToVm() throws Exception {
    Tag tag = new Tag("foo:bar=baz");
    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.addTag(vmId, tag)).thenReturn(task);

    Response response = addTag(tag);
    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testGetVmNetwork() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.getNetworks("vm1")).thenReturn(task);

    Response response = client().target(vmNetworksRoute).request().get();
    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testGetVmMksTicket() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.getMksTicket("vm1")).thenReturn(task);

    Response response = client().target(vmMksTicketRoute).request().get(Response.class);
    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testStartOperation() throws Exception {
    testVmOperation("START_VM", Response.Status.CREATED, vmStartOperationsRoute);
  }

  @Test
  public void testStopOperation() throws Exception {
    testVmOperation("STOP_VM", Response.Status.CREATED, vmStopOperationsRoute);
  }

  @Test
  public void testRestartOperation() throws Exception {
    testVmOperation("RESTART_VM", Response.Status.CREATED, vmRestartOperationsRoute);
  }

  @Test
  public void testSuspendOperation() throws Exception {
    testVmOperation("SUSPEND_VM", Response.Status.CREATED, vmSuspendOperationsRoute);
  }

  @Test
  public void testResumeOperation() throws Exception {
    testVmOperation("RESUME_VM", Response.Status.CREATED, vmResumeOperationsRoute);
  }

  @Test
  public void testDeleteVm() throws Exception {

    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.delete(vmId)).thenReturn(task);

    Response response = client().target(vmRoute).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  private void testVmOperation(String opName, Response.Status status, String uri) throws Exception {
    Operation operation = Operation.valueOf(opName);
    VmOperation vmOperation = new VmOperation();
    vmOperation.setOperation(opName);

    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.operate(vmId, operation)).thenReturn(task);

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(vmOperation, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(status.getStatusCode()));
    if (response.getStatus() == 201) {
      Task responseTask = response.readEntity(Task.class);
      assertThat(responseTask, Matchers.is(task));
      assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
      assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
    }
  }

  private Response addTag(Tag tag) {
    return client()
        .target(vmTagsRoute)
        .request()
        .post(Entity.entity(tag, MediaType.APPLICATION_JSON_TYPE));
  }
}
