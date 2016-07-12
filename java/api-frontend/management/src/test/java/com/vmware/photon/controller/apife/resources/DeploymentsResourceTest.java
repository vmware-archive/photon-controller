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
import com.vmware.photon.controller.api.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.builders.StatsInfoBuilder;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentsResource;
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
 * Tests {@link com.vmware.photon.controller.apife.resources.deployment.DeploymentsResource}.
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
    spec.setAuth(new AuthConfigurationSpecBuilder().build());
    spec.setNtpEndpoint("0.0.0.0");
    spec.setSyslogEndpoint("0.0.0.1");
    spec.setImageDatastores(Collections.singleton("imageDatastore"));
    spec.setNetworkConfiguration(new NetworkConfigurationCreateSpecBuilder().build());
    spec.setStats(new StatsInfoBuilder().build());

    addResource(new DeploymentsResource(deploymentFeClient));
  }

  @Test
  public void testSuccessfulDeploymentWithAllDisabled() throws Exception {
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
  public void testInvalidDeploymentWithAllDisabled() throws Exception {
    spec.setAuth(new AuthConfigurationSpecBuilder().enabled(false).securityGroups(new ArrayList<String>()).build());

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
  public void testSuccessfulWithAuthEnabled() throws Exception {
    spec.setAuth(new AuthConfigurationSpecBuilder()
        .enabled(true)
        .tenant("t")
        .password("p")
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
  public void testInvalidWithAuthEnabled() throws Exception {
    spec.setAuth(new AuthConfigurationSpecBuilder()
        .enabled(true)
        .tenant("t")
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
        containsString("securityGroups size must be between 1 and 2147483647"));
  }

  @Test
  public void testInvalidSecurityGroupFormat() throws Exception {
    spec.setAuth(new AuthConfigurationSpecBuilder()
        .enabled(true)
        .tenant("t")
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
  public void testSuccessfulWithStatsEnabled() throws Exception {
    spec.setStats(new StatsInfoBuilder()
        .enabled(true)
        .storeEndpoint("10.1.1.1")
        .storePort(8080)
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
  public void testInvalidWithStatsEnabled() throws Exception {
    spec.setStats(new StatsInfoBuilder().enabled(true).storeEndpoint(null).build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(400));

    ApiError apiError = response.readEntity(ApiError.class);
    assertThat(apiError.getCode(), is("InvalidStatsConfig"));
    assertThat(apiError.getMessage(), containsString("storeEndpoint may not be null"));
    assertThat(apiError.getMessage(), containsString("storePort may not be null"));
  }

  @Test
  public void testSuccessfulWithNetworkConfigEnabled() throws Exception {
    spec.setNetworkConfiguration(new NetworkConfigurationCreateSpecBuilder()
        .virtualNetworkEnabled(true)
        .networkManagerAddress("10.1.1.1")
        .networkManagerUsername("u")
        .networkManagerPassword("p")
        .networkTopRouterId("rid")
        .networkZoneId("zid")
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
  public void testInvalidWithNetworkConfigEnabled() throws Exception {
    spec.setNetworkConfiguration(new NetworkConfigurationCreateSpecBuilder()
        .virtualNetworkEnabled(true)
        .build());

    Task task = new Task();
    task.setId(taskId);
    when(deploymentFeClient.create(spec)).thenReturn(task);

    Response response = createDeployment(spec);

    assertThat(response.getStatus(), is(400));

    ApiError apiError = response.readEntity(ApiError.class);
    assertThat(apiError.getCode(), is("InvalidNetworkConfig"));
    assertThat(apiError.getMessage(), containsString("networkManagerUsername may not be null"));
    assertThat(apiError.getMessage(), containsString("networkTopRouterId may not be null"));
    assertThat(apiError.getMessage(), containsString("networkManagerAddress is invalid IP or Domain address"));
    assertThat(apiError.getMessage(), containsString("networkManagerPassword may not be null"));
    assertThat(apiError.getMessage(), containsString("networkZoneId may not be null"));
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
    deployment.setStats(new StatsInfoBuilder().build());
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
