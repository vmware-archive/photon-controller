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

import com.vmware.photon.controller.api.frontend.clients.DeploymentFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.deployment.DeploymentsResource;
import com.vmware.photon.controller.api.frontend.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.model.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.model.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.model.builders.StatsInfoBuilder;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.deployment.DeploymentsResource}.
 */
public class DeploymentsResourceTest extends ResourceTest {

  @Mock
  private DeploymentFeClient deploymentFeClient;

  private DeploymentCreateSpec spec;

  private String taskId = "task1";

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
  public void testGetEmptyDeploymentList() throws ExternalException {
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

}
