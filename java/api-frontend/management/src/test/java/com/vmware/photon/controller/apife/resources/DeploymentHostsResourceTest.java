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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.resources.host.DeploymentHostsResource;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.host.DeploymentHostsResource}.
 */

public class DeploymentHostsResourceTest extends ResourceTest {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentHostsResourceTest.class);

  private static final String deploymentId = "deployment_id";
  Host host1 = new Host();
  Host host2 = new Host();
  private String taskId = "task1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private String hostsRoute =
      UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_HOSTS_PATH).build(deploymentId).toString();
  @Mock
  private DeploymentFeClient deploymentFeClient;
  @Mock
  private HostFeClient hostFeClient;
  private PaginationConfig paginationConfig = new PaginationConfig();

  @Override
  protected void setUpResources() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
    addResource(new DeploymentHostsResource(deploymentFeClient, hostFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setup() {
    host1.setId("h1");
    host2.setId("h2");
  }

  @Test
  public void testGetHostsPage() throws Throwable {
    ResourceList<Host> expectedHostsPage = new ResourceList<>(ImmutableList.of(host1, host2),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString());
    doReturn(expectedHostsPage).when(deploymentFeClient).getHostsPage(anyString());
    Response response = getDeploymentHosts(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Host> hosts = response.readEntity(
        new GenericType<ResourceList<Host>>() {
        }
    );
    assertThat(hosts.getItems().size(), is(expectedHostsPage.getItems().size()));

    for (int i = 0; i < hosts.getItems().size(); i++) {
      assertThat(new URI(hosts.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(hosts.getItems().get(i), is(expectedHostsPage.getItems().get(i)));

      String hostsRoutePath = UriBuilder.fromPath(HostResourceRoutes.HOST_PATH).build(hosts.getItems().get(i).getId())
          .toString();
      assertThat(hosts.getItems().get(i).getSelfLink().endsWith(hostsRoutePath), is(true));
    }

    verifyPageLinks(hosts);
  }

  @Test(dataProvider = "pageSizes")
  public void testGetDeploymentHosts(Optional<Integer> pageSize, List<Host> expectedHosts) throws Throwable {
    when(deploymentFeClient.listHosts(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(host1, host2), null, null));
    when(deploymentFeClient.listHosts(deploymentId, Optional.absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(host1, host2), null, null));
    when(deploymentFeClient.listHosts(deploymentId, Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(host1), UUID.randomUUID().toString(), null));
    when(deploymentFeClient.listHosts(deploymentId, Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(host1, host2), null, null));
    when(deploymentFeClient.listHosts(deploymentId, Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = getDeploymentHosts(pageSize);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Host> result = response.readEntity(new GenericType<ResourceList<Host>>() {});
    assertThat(result.getItems().size(), is(expectedHosts.size()));

    for (int i = 0; i < result.getItems().size(); i++) {
      assertThat(new URI(result.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(result.getItems().get(i), is(expectedHosts.get(i)));

      String vmRoutePath = UriBuilder.fromPath(HostResourceRoutes.HOST_PATH).build(expectedHosts.get(i).getId())
          .toString();
      assertThat(result.getItems().get(i).getSelfLink().endsWith(vmRoutePath), is(true));
    }

    verifyPageLinks(result);
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getDeploymentHosts(Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Matchers.is("InvalidPageSize"));
    assertThat(errors.getMessage(), Matchers.is("The page size '200' is not between '1' and '100'"));
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
          add(UsageTag.CLOUD);
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
          add(UsageTag.CLOUD);
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
  public void testCreateHostInvalidMetadata() throws ExternalException {
    Map<String, String> hostMetadata = new HashMap<>();

    HostCreateSpec hostCreateSpec = new HostCreateSpec("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        new ArrayList<UsageTag>() {{
          add(UsageTag.MGMT);
        }},
        hostMetadata);

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
    doReturn(resourceList)
        .when(deploymentFeClient)
        .listHosts(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

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
    doThrow(new DeploymentNotFoundException(deploymentId)).when(deploymentFeClient).listHosts(deploymentId,
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    Response clientResponse = client()
        .target(hostsRoute)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(404));
  }

  private Response getDeploymentHosts(String pageLink) {
    String uri = hostsRoute + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getDeploymentHosts(Optional<Integer> pageSize) {
    String uri = hostsRoute;
    if (pageSize.isPresent()) {
      uri += "?pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Host> resourceList) {
    String expectedPrefix = hostsRoute + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][]{
        {
            Optional.absent(),
            ImmutableList.of(host1, host2)
        },
        {
            Optional.of(1),
            ImmutableList.of(host1)
        },
        {
            Optional.of(2),
            ImmutableList.of(host1, host2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
