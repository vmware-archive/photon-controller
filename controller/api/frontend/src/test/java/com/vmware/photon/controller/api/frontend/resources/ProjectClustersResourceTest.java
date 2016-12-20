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

import com.vmware.photon.controller.api.frontend.clients.ClusterFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.resources.cluster.ProjectClustersResource;
import com.vmware.photon.controller.api.frontend.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.when;

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
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.cluster.ProjectClustersResource}.
 */
public class ProjectClustersResourceTest extends ResourceTest {
  private static final String clusterId = "clusterId1";
  private static final String clusterName = "clusterName1";
  private static final String taskId = "taskId1";
  private static final String projectId = "projectId1";

  private static final String projectClusterRoute =
      UriBuilder.fromPath(ClusterResourceRoutes.PROJECT_CLUSTERS_PATH).build(projectId).toString();
  private static final String taskRoute =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ClusterFeClient clusterFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();
  private Cluster c1 = createCluster("clusterId1", "clusterName1");
  private Cluster c2 = createCluster("clusterId2", "clusterName2");

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    addResource(new ProjectClustersResource(clusterFeClient, paginationConfig));
  }

  private Cluster createCluster(String id, String name) {
    Cluster c = new Cluster();
    c.setId(id);
    c.setType(ClusterType.KUBERNETES);
    c.setName(name);
    c.setProjectId(projectId);
    c.setWorkerCount(3);
    c.setExtendedProperties(ImmutableMap.of(
        ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
    return c;
  }

  private ClusterCreateSpec createClusterCreateSpec() {
    ClusterCreateSpec s = new ClusterCreateSpec();
    s.setName(clusterName);
    s.setType(ClusterType.KUBERNETES);
    s.setVmFlavor("vmFlavor1");
    s.setDiskFlavor("diskFlavor1");
    s.setWorkerCount(50);
    s.setExtendedProperties(ImmutableMap.of(
        ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
    return s;
  }

  private void verifyPageLinks(ResourceList<Cluster> resourceList) {
    String expectedPrefix = projectClusterRoute + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }

  private Response getClusters(Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(projectClusterRoute);

    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  @Test(dataProvider = "pageSizes")
  public void testFindClustersInProject(Optional<Integer> pageSize, List<Cluster> expectedClusters) throws Exception {
    doReturn(new ResourceList<>(ImmutableList.of(c1, c2)))
        .when(clusterFeClient).find(projectId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(ImmutableList.of(c1), UUID.randomUUID().toString(), null))
        .when(clusterFeClient).find(projectId, Optional.of(1));
    doReturn(new ResourceList<>(ImmutableList.of(c1, c2)))
        .when(clusterFeClient).find(projectId, Optional.of(2));
    doReturn(new ResourceList<>(Collections.emptyList()))
        .when(clusterFeClient).find(projectId, Optional.of(3));

    Response response = getClusters(pageSize, Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Cluster> clusters = response.readEntity(
        new GenericType<ResourceList<Cluster>>() {
        });
    assertThat(clusters.getItems().size(), is(expectedClusters.size()));

    for (int i = 0; i < clusters.getItems().size(); i++) {
      Cluster retrievedCluster = clusters.getItems().get(i);

      assertThat(retrievedCluster, is(expectedClusters.get(i)));
      assertThat(new URI(retrievedCluster.getSelfLink()).isAbsolute(), is(true));
      assertThat(retrievedCluster.getSelfLink().endsWith(UriBuilder.fromPath(ClusterResourceRoutes.CLUSTERS_PATH)
          .build(retrievedCluster.getId()).toString()), is(true));
    }

    verifyPageLinks(clusters);
  }

  @Test
  public void testInvalidPageSize() throws ExternalException {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getClusters(Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testGetClustersPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(c1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(clusterFeClient).getClustersPage(pageLink);

    Response response = getClusters(Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Cluster> clusters = response.readEntity(new GenericType<ResourceList<Cluster>>(){});
    assertThat(clusters.getItems().size(), is(1));

    Cluster cluster = clusters.getItems().get(0);
    assertThat(cluster, is(c1));
    assertThat(new URI(cluster.getSelfLink()).isAbsolute(), is(true));
    assertThat(cluster.getSelfLink().endsWith(UriBuilder.fromPath(ClusterResourceRoutes.CLUSTERS_PATH).build(cluster
        .getId()).toString()), is(true));
  }

  @Test
  public void testInvalidClustersPageLink() throws ExternalException {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(clusterFeClient).getClustersPage(pageLink);

    Response response = getClusters(Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  @Test
  public void testCreateCluster() throws Exception {
    ClusterCreateSpec c1 = createClusterCreateSpec();

    Task t1 = new Task();
    t1.setId(taskId);
    when(clusterFeClient.create(projectId, c1)).thenReturn(t1);

    Response response = client()
        .target(projectClusterRoute)
        .request()
        .post(Entity.entity(c1, MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task t2 = response.readEntity(Task.class);
    assertThat(t2, is(t1));
    assertThat(new URI(t2.getSelfLink()).isAbsolute(), is(true));
    assertThat(t2.getSelfLink().endsWith(taskRoute), is(true));
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][]{
        {
            Optional.absent(),
            ImmutableList.of(c1, c2)
        },
        {
            Optional.of(1),
            ImmutableList.of(c1)
        },
        {
            Optional.of(2),
            ImmutableList.of(c1, c2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
