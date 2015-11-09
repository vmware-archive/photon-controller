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

import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicketReservation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.ProjectFeClient;
import com.vmware.photon.controller.apife.resources.routes.ProjectResourceRoutes;
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
import java.util.Arrays;

/**
 * Tests {@link TenantProjectsResource}.
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

  private ProjectCreateSpec spec;
  private ResourceTicketReservation ticket;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new TenantProjectsResource(projectFeClient));

    ticket = new ResourceTicketReservation();
    ticket.setName("rt1name");
    ticket.setLimits(ImmutableList.of(new QuotaLineItem("a", 1.0, QuotaUnit.COUNT)));

    spec = new ProjectCreateSpec();
    spec.setName("p1name");
    spec.setResourceTicket(ticket);
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

  @Test
  public void testGetAllProjects() throws Exception {
    Project p1 = new Project();
    p1.setName("p1name");
    p1.setId("p1");

    Project p2 = new Project();
    p2.setName("p2name");
    p2.setId("p2");

    when(projectFeClient.find(tenantId, Optional.<String>absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1, p2)));

    Response response = getProjects(Optional.<String>absent());
    assertThat(response.getStatus(), is(200));

    ResourceList<Project> projects = response.readEntity(
        new GenericType<ResourceList<Project>>() {
        }
    );

    assertThat(projects.getItems().size(), is(2));
    assertThat(projects.getItems().get(0), is(p1));
    assertThat(p1.getSelfLink().endsWith(
            UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH).build("p1").toString()),
        CoreMatchers.is(true));
    assertThat(projects.getItems().get(1), is(p2));
    assertThat(p2.getSelfLink().endsWith(
            UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH).build("p2").toString()),
        CoreMatchers.is(true));

    for (Project p : projects.getItems()) {
      assertThat(new URI(p.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    }
  }

  @Test
  public void testGetProjectsByName() throws Exception {
    Project p1 = new Project();
    p1.setName("p1name");
    p1.setId("p1");

    when(projectFeClient.find(tenantId, Optional.of("p1name")))
        .thenReturn(new ResourceList<>(ImmutableList.of(p1)));

    Response response = getProjects(Optional.of("p1name"));

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

  private Response createProject() {
    return client()
        .target(tenantProjectsRoute)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getProjects(Optional<String> name) {
    WebTarget resource = client().target(tenantProjectsRoute);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get();
  }
}
