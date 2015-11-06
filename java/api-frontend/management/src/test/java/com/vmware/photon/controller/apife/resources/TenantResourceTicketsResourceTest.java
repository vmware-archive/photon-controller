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

import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicket;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.ResourceTicketFeClient;
import com.vmware.photon.controller.apife.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;

/**
 * Tests {@link TenantResourceTicketsResource}.
 */
public class TenantResourceTicketsResourceTest extends ResourceTest {

  private String tenantId = "t1";

  private String tenantResourceTicketsRoute =
      UriBuilder.fromPath(TenantResourceRoutes.TENANT_RESOURCE_TICKETS_PATH).build(tenantId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ResourceTicketFeClient resourceTicketFeClient;

  private ResourceTicketCreateSpec spec;

  @Override
  protected void setUpResources() throws Exception {
    spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");
    spec.setLimits(ImmutableList.of(new QuotaLineItem("a", 1.0, QuotaUnit.COUNT)));

    addResource(new TenantResourceTicketsResource(resourceTicketFeClient));
  }

  @Test
  public void testSuccessfulCreateResourceTicket() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(resourceTicketFeClient.create(tenantId, spec)).thenReturn(task);

    Response response = createResourceTicket();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testFailedCreateResourceTicket() throws Exception {
    when(resourceTicketFeClient.create(tenantId, spec)).thenThrow(new ExternalException("failed"));
    assertThat(createResourceTicket().getStatus(), is(500));
  }

  @Test
  public void testInvalidResourceTicket() throws Exception {
    spec.setName(" bad name ");
    assertThat(createResourceTicket().getStatus(), is(400));
  }

  @Test
  public void testInvalidResourceTicketNegativeValue() throws Exception {
    spec.setLimits(ImmutableList.of(new QuotaLineItem("a", -1.0, QuotaUnit.COUNT)));
    assertThat(createResourceTicket().getStatus(), is(400));
  }

  @Test
  public void testInvalidResourceTicketEmptyValue() throws Exception {
    spec.setLimits(ImmutableList.<QuotaLineItem>of());
    assertThat(createResourceTicket().getStatus(), is(400));
  }

  @Test
  public void testGetAllResourceTickets() throws Exception {
    ResourceTicket rt1 = new ResourceTicket();
    rt1.setId("rt1");
    rt1.setName("rt1name");

    ResourceTicket rt2 = new ResourceTicket();
    rt2.setId("rt2");
    rt2.setName("rt2name");

    when(resourceTicketFeClient.find(tenantId, Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(rt1, rt2)));

    Response response = getResourceTickets(Optional.<String>absent());
    assertThat(response.getStatus(), is(200));

    List<ResourceTicket> tickets = response.readEntity(
        new GenericType<ResourceList<ResourceTicket>>() {
        }
    ).getItems();

    assertThat(tickets.size(), is(2));
    assertThat(tickets.get(0), is(rt1));
    assertThat(rt1.getSelfLink().endsWith(
            UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH).build("rt1").toString()),
        CoreMatchers.is(true));
    assertThat(tickets.get(1), is(rt2));
    assertThat(rt2.getSelfLink().endsWith(
            UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH).build("rt2").toString()),
        CoreMatchers.is(true));

    for (ResourceTicket t : tickets) {
      assertThat(new URI(t.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    }
  }

  @Test
  public void testGetResourceTicketsByName() throws Exception {
    ResourceTicket rt1 = new ResourceTicket();
    rt1.setId("rt1");
    rt1.setName("rt1name");

    when(resourceTicketFeClient.find(tenantId, Optional.of("rt1name")))
        .thenReturn(new ResourceList<>(ImmutableList.of(rt1)));

    Response response = getResourceTickets(Optional.of("rt1name"));
    assertThat(response.getStatus(), is(200));

    List<ResourceTicket> tickets = response.readEntity(
        new GenericType<ResourceList<ResourceTicket>>() {
        }
    ).getItems();

    assertThat(tickets.size(), is(1));
    assertThat(tickets.get(0), is(rt1));
    assertThat(rt1.getSelfLink().endsWith(
            UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH).build("rt1").toString()),
        CoreMatchers.is(true));
    assertThat(new URI(rt1.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
  }

  private Response createResourceTicket() {
    return client()
        .target(tenantResourceTicketsRoute)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getResourceTickets(Optional<String> name) {
    WebTarget resource = client().target(tenantResourceTicketsRoute);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get();
  }
}
