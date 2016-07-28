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

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.ProjectFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.resources.project.TenantProjectsResource;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.project.TenantProjectsResource}.
 */
public class TenantProjectsResourceTest extends ResourceTest {

  private String tenantId = "t1";

  private String tenantProjectsRoute =
      UriBuilder.fromPath(TenantResourceRoutes.TENANT_PROJECTS_PATH).build(tenantId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ProjectFeClient projectFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();
  private ProjectCreateSpec spec;
  private ResourceTicketReservation ticket;
  private Project p1 = new Project();
  private Project p2 = new Project();

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);

    addResource(new TenantProjectsResource(projectFeClient, paginationConfig));

    ticket = new ResourceTicketReservation();
    ticket.setName("rt1name");
    ticket.setLimits(ImmutableList.of(new QuotaLineItem("a", 1.0, QuotaUnit.COUNT)));

    spec = new ProjectCreateSpec();
    spec.setName("p1name");
    spec.setResourceTicket(ticket);

    p1.setName("p1name");
    p1.setId("p1");

    p2.setName("p2name");
    p2.setId("p2");
  }

  @Test
  public void testSuccessfulCreateProject() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(projectFeClient.create(tenantId, spec)).thenReturn(task);

    Response response = createProject();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testFailedCreateProject() throws Exception {
    when(projectFeClient.create(tenantId, spec)).thenThrow(new ExternalException("failed"));
    assertThat(createProject().getStatus(), is(500));
  }

  @Test
  public void testInvalidProject() throws Exception {
    spec.setName(" bad name ");
    assertThat(createProject().getStatus(), is(400));
  }

  @Test
  public void testInvalidProjectTicket() throws Exception {
    ticket.setLimits(ImmutableList.of(new QuotaLineItem("a", -1.0, QuotaUnit.COUNT)));
    assertThat(createProject().getStatus(), is(400));
  }

  @Test
  public void testEmptyLimitsResourceTicket() throws Exception {
    ticket.setLimits(ImmutableList.<QuotaLineItem>of());
    assertThat(createProject().getStatus(), is(400));
  }

  @Test
  public void testMissingResourceTicket() throws Exception {
    spec.setResourceTicket(null);
    assertThat(createProject().getStatus(), is(400));
  }

  @Test
  public void testInvalidSecurityGroup() throws Exception {
    spec.setSecurityGroups(Arrays.asList(new String[]{"adminGroup1"}));
    assertThat(createProject().getStatus(), is(400));
  }

  @Test(dataProvider = "pageSizes")
  public void testGetProjects(Optional<Integer> pageSize, List<Project> expectedProjects) throws Exception {
    when(projectFeClient.find(tenantId, Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1, p2)));
    when(projectFeClient.find(tenantId, Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1), UUID.randomUUID().toString(), null));
    when(projectFeClient.find(tenantId, Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1, p2)));
    when(projectFeClient.find(tenantId, Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<Project>(Collections.emptyList()));

    Response response = getProjects(Optional.<String>absent(), pageSize, Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Project> projects = response.readEntity(
        new GenericType<ResourceList<Project>>() {
        }
    );
    assertThat(projects.getItems().size(), is(expectedProjects.size()));

    for (int i = 0; i < projects.getItems().size(); i++) {
      Project retrievedProject = projects.getItems().get(i);
      assertThat(retrievedProject, is(expectedProjects.get(i)));
      assertThat(new URI(retrievedProject.getSelfLink()).isAbsolute(), is(true));
      assertThat(retrievedProject.getSelfLink().endsWith(UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH)
          .build(expectedProjects.get(i).getId()).toString()), is(true));
    }

    verifyPageLinks(projects);
  }

  @Test
  public void testInvalidPageSize() throws ExternalException {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getProjects(Optional.<String>absent(), Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testGetProjectsPageByName() throws Exception {
    when(projectFeClient.find(tenantId, Optional.of("p1name"), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1)));

    Response response = getProjects(Optional.of("p1name"), Optional.absent(), Optional.absent());

    assertThat(response.getStatus(), is(200));

    ResourceList<Project> projects = response.readEntity(
        new GenericType<ResourceList<Project>>() {
        }
    );

    assertThat(projects.getItems().size(), is(1));
    assertThat(projects.getItems().get(0), is(p1));
    assertThat(p1.getSelfLink().endsWith(
            UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH).build("p1").toString()),
        CoreMatchers.is(true));
    assertThat(new URI(p1.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
  }

  @Test
  public void testGetProjectsPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(p1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(projectFeClient)
        .getProjectsPage(pageLink);

    Response response = getProjects(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Project> projects = response.readEntity(new GenericType<ResourceList<Project>>(){});
    assertThat(projects.getItems().size(), is(1));

    Project project = projects.getItems().get(0);
    assertThat(project, is(p1));

    String projectRoutePath = UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH).build(p1.getId()).toString();
    assertThat(project.getSelfLink().endsWith(projectRoutePath), is(true));
    assertThat(new URI(project.getSelfLink()).isAbsolute(), is(true));

    verifyPageLinks(projects);
  }

  @Test
  public void testInvalidProjectsPageLink() throws ExternalException {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(projectFeClient).getProjectsPage(pageLink);

    Response response = getProjects(Optional.<String>absent(), Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  private Response createProject() {
    return client()
        .target(tenantProjectsRoute)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getProjects(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(tenantProjectsRoute);
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

  private void verifyPageLinks(ResourceList<Project> resourceList) {
    String expectedPrefix = tenantProjectsRoute + "?pageLink=";

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
            ImmutableList.of(p1, p2)
        },
        {
            Optional.of(1),
            ImmutableList.of(p1)
        },
        {
            Optional.of(2),
            ImmutableList.of(p1, p2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
