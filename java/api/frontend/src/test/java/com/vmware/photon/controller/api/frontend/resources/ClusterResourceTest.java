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
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.cluster.ClusterResource;
import com.vmware.photon.controller.api.frontend.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterState;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.cluster.ClusterResource}.
 */
public class ClusterResourceTest extends ResourceTest {

  private final String clusterId = "clusterId1";
  private final String clusterName = "clusterName1";
  private final String taskId = "taskId1";
  private final String projectId = "projectId1";

  private final String clusterRoute =
      UriBuilder.fromPath(ClusterResourceRoutes.CLUSTERS_PATH).build(clusterId).toString();
  private final String taskRoute =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ClusterFeClient clusterFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ClusterResource(clusterFeClient));
  }

  private Cluster createCluster() {
    Cluster c = new Cluster();
    c.setId(clusterId);
    c.setType(ClusterType.KUBERNETES);
    c.setState(ClusterState.READY);
    c.setName(clusterName);
    c.setProjectId(projectId);
    c.setWorkerCount(3);
    c.setExtendedProperties(ImmutableMap.of(
        ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
    return c;
  }

  @Test
  public void testGetClusterById() throws ExternalException {
    Cluster c1 = createCluster();

    when(clusterFeClient.get(clusterId)).thenReturn(c1);

    Response response = client().target(clusterRoute).request().get();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    Cluster c2 = response.readEntity(Cluster.class);
    assertThat(c2.toString(), is(c1.toString()));
    assertThat(c2, is(c1));
  }

  @Test
  public void testDeleteClusterById() throws Exception {
    Task t1 = new Task();
    t1.setId(taskId);
    when(clusterFeClient.delete(clusterId)).thenReturn(t1);

    Response response = client().target(clusterRoute).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task t2 = response.readEntity(Task.class);
    assertThat(t2, is(t1));
    assertThat(new URI(t2.getSelfLink()).isAbsolute(), is(true));
    assertThat(t2.getSelfLink().endsWith(taskRoute), is(true));
  }
}
