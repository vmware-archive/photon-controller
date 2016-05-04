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
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.apife.resources.vm.DeploymentVmsResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.vm.DeploymentVmsResource}.
 */

public class DeploymentVmsResourceTest extends ResourceTest {

  private static final String deploymentId = "deployment_id";

  private String vmsRoute =
      UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_VMS_PATH).build(deploymentId).toString();

  @Mock
  private DeploymentFeClient deploymentFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();
  private Vm vm1 = new Vm();
  private  Vm vm2 = new Vm();

  @Override
  protected void setUpResources() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    vm1.setId("vm1");
    vm1.setName("vm1name");

    vm2.setId("vm2");
    vm2.setName("vm1name");

    addResource(new DeploymentVmsResource(deploymentFeClient, paginationConfig));
  }

  @Test(dataProvider = "deploymentVmsPageSizes")
  public void testListVms(Optional<Integer> pageSize, List<Vm> expectedVms) throws Throwable {
    doReturn(new ResourceList<>(ImmutableList.of(vm1), UUID.randomUUID().toString(), null))
        .when(deploymentFeClient)
        .listVms(deploymentId, Optional.of(1));
    doReturn(new ResourceList<>(ImmutableList.of(vm1, vm2)))
        .when(deploymentFeClient)
        .listVms(deploymentId, Optional.of(2));
    doReturn(new ResourceList<>(ImmutableList.of(vm1, vm2)))
        .when(deploymentFeClient)
        .listVms(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(Collections.emptyList()))
        .when(deploymentFeClient)
        .listVms(deploymentId, Optional.of(3));

    Response response = getVms(pageSize, Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Vm> vms = response.readEntity(new GenericType<ResourceList<Vm>>() {});

    assertThat(vms.getItems().size(), is(expectedVms.size()));
    for (int i = 0; i < vms.getItems().size(); i++) {
      Vm retrievedVm = vms.getItems().get(i);
      assertThat(retrievedVm, is(expectedVms.get(i)));

      String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(retrievedVm.getId()).toString();
      assertThat(new URI(retrievedVm.getSelfLink()).isAbsolute(), is(true));
      assertThat(retrievedVm.getSelfLink().endsWith(vmRoutePath), is(true));
    }

    verifyPageLinks(vms);
  }

  @Test
  public void testInvalidPageSize() {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getVms(Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testListVmsPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(ImmutableList.of(vm1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(deploymentFeClient).getVmsPage(pageLink);

    Response response = getVms(Optional.absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Vm> vms = response.readEntity(new GenericType<ResourceList<Vm>>(){});
    assertThat(vms.getItems().size(), is(1));

    Vm vm = vms.getItems().get(0);
    assertThat(vm, is(vm1));
    assertThat(new URI(vm.getSelfLink()).isAbsolute(), is(true));

    String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vm1.getId()).toString();
    assertThat(vm.getSelfLink().endsWith(vmRoutePath), is(true));
  }

  @Test
  public void testInvalidPageLink() throws ExternalException {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(deploymentFeClient).getVmsPage(pageLink);

    Response response = getVms(Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  @Test
  public void testFailedOnDeploymentNotFound() throws Throwable {
    doThrow(new DeploymentNotFoundException(deploymentId))
        .when(deploymentFeClient)
        .listVms(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    Response clientResponse = client()
        .target(vmsRoute)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(404));
  }

  private Response getVms(Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(vmsRoute);

    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Vm> resourceList) {
    String expectedPrefix = vmsRoute + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "deploymentVmsPageSizes")
  private Object[][] getDeploymentVmsPageSizes() {
    return new Object[][] {
        {
            Optional.absent(),
            ImmutableList.of(vm1, vm2)
        },
        {
            Optional.of(1),
            ImmutableList.of(vm1)
        },
        {
            Optional.of(2),
            ImmutableList.of(vm1, vm2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        },
    };
  }
}
