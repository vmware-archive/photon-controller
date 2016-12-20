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

import com.vmware.photon.controller.api.frontend.clients.ProjectFeClient;
import com.vmware.photon.controller.api.frontend.resources.project.ProjectResource;
import com.vmware.photon.controller.api.frontend.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.Task;

import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.project.ProjectResource}.
 */
public class ProjectResourceTest extends ResourceTest {

  private String projectId = "p1";

  private String projectRoutePath =
      UriBuilder.fromPath(ProjectResourceRoutes.PROJECT_PATH).build(projectId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ProjectFeClient projectFeClient;

  @Override
  protected void setUpResources() throws Exception {
    ProjectResource resource = new ProjectResource(projectFeClient);
    addResource(resource);
  }

  @Test
  public void testGetProjectById() throws Exception {
    Project project = new Project();
    project.setId(projectId);
    when(projectFeClient.get(projectId)).thenReturn(project);

    Response response = client().target(projectRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    Project responseProject = response.readEntity(Project.class);
    assertThat(responseProject, CoreMatchers.is(project));
    assertThat(new URI(responseProject.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseProject.getSelfLink().endsWith(projectRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testDeleteProject() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(projectFeClient.delete(projectId)).thenReturn(task);

    Response response = client().target(projectRoutePath).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }
}
