/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import com.vmware.photon.controller.api.frontend.resources.info.InfoResource;
import com.vmware.photon.controller.api.frontend.resources.routes.InfoResourceRoutes;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.Info;
import com.vmware.photon.controller.api.model.NetworkConfiguration;
import com.vmware.photon.controller.api.model.NetworkType;
import com.vmware.photon.controller.api.model.ResourceList;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tests {@link InfoResource}.
 */
public class InfoResourceTest extends ResourceTest {

  @Mock
  private DeploymentFeClient deploymentFeClient;

  @Override
  protected void setUpResources() {
    addResource(new InfoResource(deploymentFeClient));
  }

  @Test(enabled = false)
  public void dummy() {
  }

  @Test
  public void testGetInfoSuccess() throws ExternalException {
    NetworkConfiguration networkConfiguration = new NetworkConfiguration();
    networkConfiguration.setSdnEnabled(true);

    Deployment deployment = new Deployment();
    deployment.setId("deployment-id");
    deployment.setNetworkConfiguration(networkConfiguration);

    ResourceList<Deployment> resourceList = new ResourceList<>();
    resourceList.setItems(Arrays.asList(deployment));

    doReturn(resourceList).when(deploymentFeClient).listAllDeployments();

    Response response = client().target(InfoResourceRoutes.API).request().get();
    assertThat(response.getStatus(), is(200));
    Info info = response.readEntity(Info.class);
    assertThat(info.getNetworkType(), is(NetworkType.SOFTWARE_DEFINED));

    // Note that the following information comes from our JAR file, which we don't have
    // during unit tests. Therefore we just make sure they're non-empty. Integration
    // tests will validate this information.
    assertThat(info.getBaseVersion(), not(equalTo(null)));
    assertThat(info.getFullVersion(), not(equalTo(null)));
    assertThat(info.getGitCommitHash(), not(equalTo(null)));
  }

  @Test
  public void testGetInfoDeploymentNotExist() throws ExternalException {
    ResourceList<Deployment> resourceList = new ResourceList<>();
    resourceList.setItems(new ArrayList<>());

    doReturn(resourceList).when(deploymentFeClient).listAllDeployments();

    Response response = client().target(InfoResourceRoutes.API).request().get();
    assertThat(response.getStatus(), is(200));
    assertThat(response.readEntity(Info.class).getNetworkType(), is(NetworkType.NOT_AVAILABLE));
  }

  @Test
  public void testGetInfoMultipleDeploymentExist() throws ExternalException {
    ResourceList<Deployment> resourceList = new ResourceList<>();
    resourceList.setItems(Arrays.asList(new Deployment(), new Deployment()));

    doReturn(resourceList).when(deploymentFeClient).listAllDeployments();

    Response response = client().target(InfoResourceRoutes.API).request().get();
    assertThat(response.getStatus(), is(200));
    assertThat(response.readEntity(Info.class).getNetworkType(), is(NetworkType.NOT_AVAILABLE));
  }
}
