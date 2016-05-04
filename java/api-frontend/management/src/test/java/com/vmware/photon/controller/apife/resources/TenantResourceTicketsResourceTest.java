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

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicket;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.ResourceTicketFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.resourceticket.TenantResourceTicketsResource;
import com.vmware.photon.controller.apife.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.resourceticket.TenantResourceTicketsResource}.
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
  private PaginationConfig paginationConfig = new PaginationConfig();
  private ResourceTicket rt1 = new ResourceTicket();
  private ResourceTicket rt2 = new ResourceTicket();

  @Override
  protected void setUpResources() throws Exception {
    spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");
    spec.setLimits(ImmutableList.of(new QuotaLineItem("a", 1.0, QuotaUnit.COUNT)));

    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    addResource(new TenantResourceTicketsResource(resourceTicketFeClient, paginationConfig));

    rt1.setId("rt1");
    rt1.setName("rt1name");

    rt2.setId("rt2");
    rt2.setName("rt2name");
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

  @Test(dataProvider = "pageSizes")
  public void testGetResourceTickets(Optional<Integer> pageSize, List<ResourceTicket> expectedResourceTickets)
      throws Exception {

    doReturn(new ResourceList<>(ImmutableList.of(rt1, rt2)))
        .when(resourceTicketFeClient)
        .find(tenantId, Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(ImmutableList.of(rt1), UUID.randomUUID().toString(), null))
        .when(resourceTicketFeClient)
        .find(tenantId, Optional.<String>absent(), Optional.of(1));
    doReturn(new ResourceList<>(ImmutableList.of(rt1, rt2)))
        .when(resourceTicketFeClient)
        .find(tenantId, Optional.<String>absent(), Optional.of(2));
    doReturn(new ResourceList<>(Collections.emptyList()))
        .when(resourceTicketFeClient)
        .find(tenantId, Optional.<String>absent(), Optional.of(3));

    Response response = getResourceTickets(Optional.<String>absent(), pageSize, Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<ResourceTicket> tickets = response.readEntity(
        new GenericType<ResourceList<ResourceTicket>>() {
        }
    );

    assertThat(tickets.getItems().size(), is(expectedResourceTickets.size()));
    for (int i = 0; i < tickets.getItems().size(); i++) {
      ResourceTicket rt = tickets.getItems().get(i);

      assertThat(rt, is(expectedResourceTickets.get(i)));
      assertThat(new URI(rt.getSelfLink()).isAbsolute(), is(true));
      assertThat(rt.getSelfLink().endsWith(UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH)
          .build(rt.getId()).toString()), is(true));
    }

    verifyPageLinks(tickets);
  }

  @Test
  public void testInvalidPageSize() {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getResourceTickets(Optional.<String>absent(), Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testGetResourceTicketsPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(rt1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(resourceTicketFeClient).getPage(pageLink);

    Response response = getResourceTickets(Optional.<String>absent(), Optional.<Integer>absent(),
        Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<ResourceTicket> resourceTickets = response.readEntity(
        new GenericType<ResourceList<ResourceTicket>>() {
        }
    );
    assertThat(resourceTickets.getItems().size(), is(1));

    ResourceTicket rt = resourceTickets.getItems().get(0);
    assertThat(rt, is(rt1));
    assertThat(new URI(rt.getSelfLink()).isAbsolute(), is(true));
    assertThat(rt.getSelfLink().endsWith(UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH)
        .build(rt.getId()).toString()), is(true));

    verifyPageLinks(resourceTickets);
  }

  @Test
  public void testInvalidPageLink() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(resourceTicketFeClient).getPage(pageLink);

    Response response = getResourceTickets(Optional.<String>absent(), Optional.<Integer>absent(),
        Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  @Test
  public void testGetResourceTicketsByName() throws Exception {
    when(resourceTicketFeClient.find(tenantId, Optional.of("rt1name"),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(rt1)));

    Response response = getResourceTickets(Optional.of("rt1name"), Optional.<Integer>absent(),
        Optional.<String>absent());
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

  private Response getResourceTickets(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(tenantResourceTicketsRoute);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }
    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }
    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<ResourceTicket> resourceList) {
    String expectedPrefix = tenantResourceTicketsRoute + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][] {
        {
            Optional.absent(),
            ImmutableList.of(rt1, rt2)
        },
        {
            Optional.of(1),
            ImmutableList.of(rt1)
        },
        {
            Optional.of(2),
            ImmutableList.of(rt1, rt2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
