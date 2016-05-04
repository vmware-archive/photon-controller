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
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentAdminGroupsResource;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Arrays;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.deployment.DeploymentAdminGroupsResource}.
 */
public class DeploymentAdminGroupsResourceTest extends ResourceTest {

  private String deploymentId = "id";
  private String taskId = "task1";

  private String deploymentRoutePath = UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_ADMIN_GROUPS_PATH)
      .build(deploymentId).toString();
  private String taskRoutePath = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private DeploymentFeClient deploymentFeClient;

  @Override
  public void setUpResources() {
    addResource(new DeploymentAdminGroupsResource(deploymentFeClient));
  }

  @Test
  public void testSetAdminGroupsSuccess() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    when(deploymentFeClient.setSecurityGroups(eq(deploymentId), anyListOf(String.class))).thenReturn(task);

    ResourceList<String> adminGroups = new ResourceList<>(Arrays.asList(new String[]{"tenant\\adminGroup1",
        "tenant\\adminGroup2"}));

    Response response = client()
        .target(deploymentRoutePath)
        .request()
        .post(Entity.entity(adminGroups, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(200));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testSetAdminGroupsFail() throws Exception {
    ResourceList<String> adminGroups = new ResourceList<>(Arrays.asList(new String[]{"tenant\\adminGroup1",
        "tenant\\adminGroup2"}));
    when(deploymentFeClient.setSecurityGroups(eq(deploymentId), anyListOf(String.class)))
        .thenThrow(new InvalidAuthConfigException("Auth is not enabled, and security groups cannot be set."));

    Response response = client()
        .target(deploymentRoutePath)
        .request()
        .post(Entity.entity(adminGroups, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidAuthConfig"));
    assertThat(errors.getMessage(), containsString("Auth is not enabled, and security groups cannot be set."));
  }

  @Test
  public void testSetAdminGroupsFailWithInvalidSecurityGroup() throws Exception {
    ResourceList<String> adminGroups = new ResourceList<>(Arrays.asList(new String[]{"adminGroup1",
        "adminGroup2"}));
    when(deploymentFeClient.setSecurityGroups(eq(deploymentId), anyListOf(String.class)))
        .thenThrow(new InvalidAuthConfigException("Auth is not enabled, and security groups cannot be set."));

    Response response = client()
        .target(deploymentRoutePath)
        .request()
        .post(Entity.entity(adminGroups, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidSecurityGroupFormat"));
    assertThat(errors.getMessage(), containsString("The security group format should match domain\\group"));
  }
}
