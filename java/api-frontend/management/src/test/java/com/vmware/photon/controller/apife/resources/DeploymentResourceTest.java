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
import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentDeployOperation;
import com.vmware.photon.controller.api.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.InitializeMigrationOperation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.builders.StatsInfoBuilder;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentResource;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.deployment.DeploymentResource}.
 */
public class DeploymentResourceTest extends ResourceTest {
  private String deploymentId = "id";

  private String taskId = "task1";

  private String deploymentRoutePath =
      UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH).build(deploymentId).toString();

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private DeploymentBackend deploymentBackend;

  @Mock
  private DeploymentFeClient feClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DeploymentResource(feClient));
  }

  @Test
  public void testGetDeploymentById() throws Exception {
    Deployment deployment = new Deployment();
    deployment.setId(deploymentId);
    deployment.setImageDatastores(Collections.singleton("imageDatastore"));
    deployment.setSyslogEndpoint("0.0.0.0");
    deployment.setNtpEndpoint("0.0.0.1");
    deployment.setStats(new StatsInfoBuilder().build());
    deployment.setAuth(new AuthInfoBuilder().build());
    deployment.setUseImageDatastoreForVms(true);

    when(feClient.get(deploymentId)).thenReturn(deployment);

    Response response = client().target(deploymentRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    Deployment deploymentRetrieved = response.readEntity(Deployment.class);
    assertThat(deploymentRetrieved, is(deployment));
    assertThat(new URI(deploymentRetrieved.getSelfLink()).isAbsolute(), is(true));
    assertThat(deploymentRetrieved.getSelfLink().endsWith(deploymentRoutePath), is(true));
  }

  @Test(dataProvider = "validDesiredState")
  public void testPerformDeployment(String key, String value) throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.perform(eq(deploymentId), any(DeploymentDeployOperation.class))).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.PERFORM_DEPLOYMENT_ACTION)
        .build(deploymentId)
        .toString();

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(buildConfig(key, value), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @DataProvider(name = "validDesiredState")
  private Object[][] getValideDesireState() {
    return new Object[][]{
        {null, null},
        {"", ""},
        {"desiredState", "PAUSED"},
        {"desiredState", "BACKGROUND_PAUSED"},
        {"desiredState", "READY"},
    };
  }

  @Test(dataProvider = "invalidDesiredState")
  public void testPerformDeploymentInvalidDesiredState(String key, String value) throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.perform(eq(deploymentId), any(DeploymentDeployOperation.class))).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.PERFORM_DEPLOYMENT_ACTION)
        .build(deploymentId)
        .toString();

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(buildConfig(key, value), MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(400));

  }

  @DataProvider(name = "invalidDesiredState")
  private Object[][] getInvalideDesireState() {
    return new Object[][]{
        {"desiredState", ""},
        {"desiredState", "INVALID_STATE"},
        {"desiredState", "CERATING"},
        {"desiredState", "ERROR"},
        {"desiredState", "CERATING"},
        {"desiredState", "NOT_DEPLOYED"},
        {"desiredState", "DELETED"},
    };
  }

  @Test
  public void testInitializeDeploymentMigration() throws Exception {
    InitializeMigrationOperation op = new InitializeMigrationOperation();
    op.setSourceLoadBalancerAddress("address");

    Task task = new Task();
    task.setId(taskId);
    when(feClient.initializeDeploymentMigration(op, deploymentId)).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.INITIALIZE_MIGRATION_ACTION)
        .build(deploymentId)
        .toString();

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(op, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testFinalizeDeploymentMigration() throws Exception {
    FinalizeMigrationOperation op = new FinalizeMigrationOperation();
    op.setSourceLoadBalancerAddress("address");

    Task task = new Task();
    task.setId(taskId);
    when(feClient.finalizeDeploymentMigration(op, deploymentId)).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.FINALIZE_MIGRATION_ACTION)
        .build(deploymentId)
        .toString();

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(op, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testSuccessfulDeleteDeployment() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.delete(deploymentId)).thenReturn(task);

    Response response = client()
        .target(deploymentRoutePath)
        .request()
        .delete();

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), Matchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), Matchers.is(true));
  }

  @Test
  public void testDeleteDeploymentByInvalidId() throws Exception {
    when(feClient.delete(deploymentId)).thenThrow(new DeploymentNotFoundException(deploymentId));

    Response response = client()
        .target(deploymentRoutePath)
        .request()
        .delete();

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("DeploymentNotFound"));
    assertThat(errors.getMessage(), containsString("Deployment #" + deploymentId + " not found"));
  }

  @Test
  public void testPauseSystem() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.pauseSystem(deploymentId)).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.PAUSE_SYSTEM_ACTION)
        .build(deploymentId)
        .toString();
    Response response = client()
        .target(uri)
        .request()
        .post(Entity.json(null));

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testPauseBackgroundTasks() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.pauseBackgroundTasks(deploymentId)).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.PAUSE_BACKGROUND_TASKS_ACTION)
        .build(deploymentId)
        .toString();
    Response response = client()
        .target(uri)
        .request()
        .post(Entity.json(null));

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testResumeSystem() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    when(feClient.resumeSystem(deploymentId)).thenReturn(task);

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.RESUME_SYSTEM_ACTION)
        .build(deploymentId)
        .toString();
    Response response = client()
        .target(uri)
        .request()
        .post(Entity.json(null));

    assertThat(response.getStatus(), Matchers.is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testConfigCluster() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(feClient).configureCluster(eq(deploymentId), any(ClusterConfigurationSpec.class));

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.ENABLE_CLUSTER_TYPE_ACTION)
        .build(deploymentId)
        .toString();

    ClusterConfigurationSpec configSpec = new ClusterConfigurationSpec();
    configSpec.setType(ClusterType.KUBERNETES);
    configSpec.setImageId("imageId");
    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(configSpec, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(200));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testDeleteClusterConfiguration() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(feClient).deleteClusterConfiguration(eq(deploymentId), any(ClusterType.class));

    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.DISABLE_CLUSTER_TYPE_ACTION)
        .build(deploymentId)
        .toString();

    ClusterConfigurationSpec spec = new ClusterConfigurationSpec();
    spec.setType(ClusterType.KUBERNETES);

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(201));
  }

  @Test
  public void testDeleteClusterConfigurationWithNullClusterType() throws Exception {
    String uri = UriBuilder
        .fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
            DeploymentResourceRoutes.DISABLE_CLUSTER_TYPE_ACTION)
        .build(deploymentId)
        .toString();

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(new ClusterConfigurationSpec(), MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void testUpdateImageDatastores() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(feClient).setImageDatastores(eq(deploymentId), anyListOf(String.class));

    String uri = UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
        DeploymentResourceRoutes.SET_IMAGE_DATASTORES_ACTION)
        .build(deploymentId)
        .toString();
    ResourceList<String> imageDatastores = new ResourceList<>(Arrays.asList(new String[]{"imageDatastore1",
        "imageDatastore2"}));

    Response response = client()
        .target(uri)
        .request()
        .post(Entity.entity(imageDatastores, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(200));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  private String buildConfig(String key, String value) {
    StringBuilder builder = new StringBuilder();
    if (key == null || key.isEmpty()) {
        builder.append("{}");
    } else {
        builder.append("{\"" + key + "\":\"" + value + "\"}");
    }

    return builder.toString();
  }
}
