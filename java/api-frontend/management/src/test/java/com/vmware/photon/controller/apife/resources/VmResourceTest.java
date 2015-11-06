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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmOperation;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;

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
 * Tests {@link VmResource}.
 * Tests {@link VmMksTicketResource}.
 * Tests {@link VmNetworksResource}.
 * Tests {@link VmOperationsResource}.
 * Tests {@link VmTagsResource}.
 */
public class VmResourceTest extends ResourceTest {

  private String vmId = "vm1";

  private String vmRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vmId).toString();

  private String vmTagsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_TAGS_PATH).build(vmId).toString();

  private String vmNetworksRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_NETWORKS_PATH).build(vmId).toString();

  private String vmMksTicketRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_MKS_TICKET_PATH).build(vmId).toString();

  private String vmOperationsRoute =
      UriBuilder.fromPath(VmResourceRoutes.VM_OPERATIONS_PATH).build(vmId).toString();

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
    addResource(new VmOperationsResource(vmFeClient));
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
  public void testGoodOperation() throws Exception {
    testVmOperation("RESTART_VM", Response.Status.CREATED);
  }

  @Test
  public void testContextuallyIllegalOperation() throws Exception {
    testVmOperation("CREATE_PROJECT", Response.Status.BAD_REQUEST);
  }

  @Test
  public void testBogusOperation() throws Exception {
    VmOperation op = new VmOperation();
    op.setOperation("FOOBAR");

    Response response = client()
        .target(vmOperationsRoute)
        .request()
        .post(Entity.entity(op, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
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

  private void testVmOperation(String opName, Response.Status status) throws Exception {
    Operation operation = Operation.valueOf(opName);
    VmOperation vmOperation = new VmOperation();
    vmOperation.setOperation(opName);

    Task task = new Task();
    task.setId(taskId);
    when(vmFeClient.operate(vmId, operation)).thenReturn(task);

    Response response = client()
        .target(vmOperationsRoute)
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
