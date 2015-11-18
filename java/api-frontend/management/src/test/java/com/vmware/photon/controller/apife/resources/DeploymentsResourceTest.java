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
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link DeploymentsResource}.
 */
public class DeploymentsResourceTest extends ResourceTest {

  @Mock
  private DeploymentFeClient deploymentFeClient;

  private DeploymentCreateSpec spec;

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Override
  protected void setUpResources() throws Exception {
    spec = new DeploymentCreateSpec();
    spec.setNtpEndpoint("0.0.0.0");
    spec.setSyslogEndpoint("0.0.0.1");
    spec.setImageDatastores(Collections.singleton("imageDatastore"));

    addResource(new DeploymentsResource(deploymentFeClient));
  }

  @Test
  public void testSuccessfulCreateDeploymentWithAuthDisabled() throws Exception {
    spec.setAuth(new AuthInfoBuilder().build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testInvalidDeploymentWithAuthDisabled() throws Exception {
    spec.setAuth(new AuthInfoBuilder().enabled(false).securityGroups(new ArrayList<String>()).build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(400));

    ApiError apiError = response.readEntity(ApiError.class);
    assertThat(apiError.getCode(), is("InvalidAuthConfig"));
    assertThat(apiError.getMessage(), containsString("securityGroups must be null"));
  }

  @Test
  public void testSuccessfulCreateDeploymentWithAuthEnabled() throws Exception {
    spec.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .tenant("t")
        .username("u")
        .password("p")
        .endpoint("https://foo")
        .securityGroups(Arrays.asList(new String[]{"t\\adminGroup1"}))
        .build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testInvalidDeploymentWithAuthEnabled() throws Exception {
    spec.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("https://foo")
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(new ArrayList<String>())
        .build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(400));

    ApiError apiError = response.readEntity(ApiError.class);
    assertThat(apiError.getCode(), is("InvalidAuthConfig"));
    assertThat(apiError.getMessage(),
        containsString("securityGroups size must be between 1 and 2147483647 (was [])"));
  }

  @Test
  public void testInvalidSecurityGroupFormatWithAuthEnabled() throws Exception {
    spec.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("https://foo")
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(Arrays.asList(new String[]{"adminGroup1"}))
        .build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(400));

    ApiError apiError = response.readEntity(ApiError.class);
    assertThat(apiError.getCode(), is("InvalidSecurityGroupFormat"));
    assertThat(apiError.getMessage(),
        containsString("The security group format should match domain\\group"));
  }

  @Test
  public void testGetEmptyDeploymentList() {
    ResourceList<Deployment> resourceList = new ResourceList<>(new ArrayList<Deployment>());
    doReturn(resourceList).when(deploymentFeClient).listAllDeployments();

    Response clientResponse = client()
        .target(DeploymentResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Deployment> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Deployment>>
        () {});
    assertThat(retrievedResources.getItems().size(), is(0));
  }

  @Test
  public void testGetDeploymentList() throws Throwable {
    Deployment deployment = new Deployment();
    deployment.setId("id");
    deployment.setImageDatastores(Collections.singleton("imageDatastore"));
    deployment.setSyslogEndpoint("0.0.0.0");
    deployment.setNtpEndpoint("0.0.0.1");
    deployment.setAuth(new AuthInfoBuilder().build());
    deployment.setUseImageDatastoreForVms(true);

    List<Deployment> deploymentList = new ArrayList<>();
    deploymentList.add(deployment);

    ResourceList<Deployment> resourceList = new ResourceList<>(deploymentList);
    doReturn(resourceList).when(deploymentFeClient).listAllDeployments();

    Response clientResponse = client()
        .target(DeploymentResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Deployment> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Deployment>>
        () {});
    assertThat(retrievedResources.getItems().size(), is(1));

    Deployment retrievedDeployment = retrievedResources.getItems().get(0);
    assertThat(retrievedDeployment, is(deployment));

    assertThat(new URI(retrievedDeployment.getSelfLink()).isAbsolute(), is(true));
    assertThat(retrievedDeployment.getSelfLink().contains(DeploymentResourceRoutes.API), is(true));
  }

  private Response createDeployment(DeploymentCreateSpec spec) {
    return client()
        .target(DeploymentResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }
}
