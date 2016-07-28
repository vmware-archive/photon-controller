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

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.clients.ClusterFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.apife.resources.vm.ClusterVmsResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.vm.ClusterVmsResource}.
 */
public class ClusterVmsResourceTest extends ResourceTest {

  private final String clusterId = "clusterId1";

  private final String clusterVmsRoute =
      UriBuilder.fromPath(ClusterResourceRoutes.CLUSTER_VMS_PATH).build(clusterId).toString();

  private PaginationConfig paginationConfig = new PaginationConfig();
  private Vm vm1 = new Vm();
  private Vm vm2 = new Vm();

  @Mock
  private ClusterFeClient clusterFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ClusterVmsResource(clusterFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setup() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    vm1.setId("vm1");
    vm2.setId("vm2");
  }

  @Test
  public void testGetVmsPage() throws Throwable {
    ResourceList<Vm> expectedVmsPage = new ResourceList<>(ImmutableList.of(vm1, vm2), UUID.randomUUID().toString(),
        UUID.randomUUID().toString());
    when(clusterFeClient.getVmsPage(anyString())).thenReturn(expectedVmsPage);

    Response response = getClusterVms(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Vm> vms = response.readEntity(
        new GenericType<ResourceList<Vm>>() {
        }
    );
    assertThat(vms.getItems().size(), is(expectedVmsPage.getItems().size()));

    for (int i = 0; i < vms.getItems().size(); i++) {
      assertThat(new URI(vms.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(vms.getItems().get(i), is(expectedVmsPage.getItems().get(i)));

      String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vms.getItems().get(i).getId())
          .toString();
      assertThat(vms.getItems().get(i).getSelfLink().endsWith(vmRoutePath), is(true));
    }

    verifyPageLinks(vms);
  }

  @Test
  public void testInvalidVmsPageLink() throws ExternalException {
    String pageLink = "randomPageLink";
    doThrow(new PageExpiredException(pageLink)).when(clusterFeClient).getVmsPage(pageLink);

    Response response = getClusterVms(pageLink);
    assertThat(response.getStatus(), Matchers.is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Matchers.is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), Matchers.is(expectedErrorMessage));
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getClusterVms(Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Matchers.is("InvalidPageSize"));
    assertThat(errors.getMessage(), Matchers.is("The page size '200' is not between '1' and '100'"));
  }

  @Test(dataProvider = "pageSizes")
  public void testGet(Optional<Integer> pageSize, List<Vm> expectedVms) throws Throwable {
    when(clusterFeClient.findVms(clusterId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(vm1, vm2), null, null));
    when(clusterFeClient.findVms(clusterId, Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(vm1), UUID.randomUUID().toString(), null));
    when(clusterFeClient.findVms(clusterId, Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(vm1, vm2), null, null));
    when(clusterFeClient.findVms(clusterId, Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = getClusterVms(pageSize);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Vm> result = response.readEntity(new GenericType<ResourceList<Vm>>() {});
    assertThat(result.getItems().size(), is(expectedVms.size()));

    for (int i = 0; i < result.getItems().size(); i++) {
      assertThat(new URI(result.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(result.getItems().get(i), is(expectedVms.get(i)));

      String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(expectedVms.get(i).getId()).toString();
      assertThat(result.getItems().get(i).getSelfLink().endsWith(vmRoutePath), is(true));
    }

    verifyPageLinks(result);
  }

  private Response getClusterVms(String pageLink) {
    String uri = clusterVmsRoute + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getClusterVms(Optional<Integer> pageSize) {
    String uri = clusterVmsRoute;
    if (pageSize.isPresent()) {
      uri += "?pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Vm> resourceList) {
    String expectedPrefix = clusterVmsRoute + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
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
        }
    };
  }
}
