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

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;

import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link DeploymentHostsResource}.
 */

public class DeploymentHostsResourceTest extends ResourceTest {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentHostsResourceTest.class);

  private static final String deploymentId = "deployment_id";
  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private String hostsRoute =
      UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_HOSTS_PATH).build(deploymentId).toString();

  @Mock
  private DeploymentFeClient deploymentFeClient;
  @Mock
  private HostFeClient hostFeClient;

  @Override
  protected void setUpResources() {
    addResource(new DeploymentHostsResource(deploymentFeClient, hostFeClient));
  }

  @Test
  public void testCreateHost() throws ExternalException, URISyntaxException {
    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        new ArrayList<UsageTag>() {{
          add(UsageTag.MGMT);
        }},
        hostMetadata);

    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(hostFeClient).createHost(eq(hostCreateSpec), anyString());

    Response clientResponse = client()
        .target(hostsRoute)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(hostCreateSpec, MediaType.APPLICATION_JSON));
    assertThat(clientResponse.getStatus(), is(201));

    Task responseTask = clientResponse.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testCreateHostFailure() throws ExternalException {
    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        new ArrayList<UsageTag>() {{
          add(UsageTag.MGMT);
        }},
        hostMetadata);
    doThrow(new ExternalException()).when(hostFeClient).createHost(eq(hostCreateSpec), anyString());

    Response clientResponse = client()
        .target(hostsRoute)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(hostCreateSpec, MediaType.APPLICATION_JSON));
    assertThat(clientResponse.getStatus(), is(500));
  }

  @Test
  public void testCreateHostWithMgmtCloudTagsSuccess() throws ExternalException, URISyntaxException {
    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");
    hostMetadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE, "8");
    hostMetadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE, "8");
    hostMetadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE, "32");

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        Arrays.asList(UsageTag.MGMT, UsageTag.CLOUD),
        hostMetadata);

    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(hostFeClient).createHost(eq(hostCreateSpec), anyString());

    Response clientResponse = client()
        .target(hostsRoute)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(hostCreateSpec, MediaType.APPLICATION_JSON));
    assertThat(clientResponse.getStatus(), is(201));

    Task responseTask = clientResponse.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testCreateHostWithMgmtCloudTagsFailure() throws ExternalException {
    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        Arrays.asList(UsageTag.MGMT, UsageTag.CLOUD),
        hostMetadata);
    doThrow(new ExternalException()).when(hostFeClient).createHost(eq(hostCreateSpec), anyString());

    Response clientResponse = client()
        .target(hostsRoute)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(hostCreateSpec, MediaType.APPLICATION_JSON));
    assertThat(clientResponse.getStatus(), is(400));
  }

  @Test
  public void testListHosts() throws Throwable {
    Host host1 = new Host();
    host1.setId("h1");

    Host host2 = new Host();
    host2.setId("h2");

    List<Host> hostList = ImmutableList.of(host1, host2);
    ResourceList<Host> resourceList = new ResourceList<>(hostList);
    doReturn(resourceList).when(deploymentFeClient).listHosts(deploymentId);

    Response clientResponse = client()
        .target(hostsRoute)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Host> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Host>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(2));
    List<Host> retrievedHostList = retrievedResources.getItems();
    for (int i = 0; i < retrievedHostList.size(); i++) {
      Host retrievedHost = retrievedHostList.get(i);
      assertThat(retrievedHost, is(hostList.get(i)));

      String hostRoutePath = UriBuilder.fromPath(HostResourceRoutes.HOST_PATH)
          .build(hostList.get(i).getId()).toString();
      assertThat(new URI(retrievedHost.getSelfLink()).isAbsolute(), is(true));
      assertThat(retrievedHost.getSelfLink().endsWith(hostRoutePath), is(true));
    }
  }

  @Test
  public void testFailedOnDeploymentNotFound() throws Throwable {
    doThrow(new DeploymentNotFoundException(deploymentId)).when(deploymentFeClient).listHosts(deploymentId);

    Response clientResponse = client()
        .target(hostsRoute)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(404));
  }
}
