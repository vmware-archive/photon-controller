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
import com.vmware.photon.controller.api.VirtualSubnet;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.apife.resources.virtualnetwork.SubnetsResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link SubnetsResource}.
 */
public class VirtualSubnetsResourceTest extends ResourceTest {

  @Mock
  private VirtualNetworkFeClient frontendClient;
  private PaginationConfig paginationConfig = new PaginationConfig();

  @Override
  public void setUpResources() throws Exception {
    addResource(new SubnetsResource(frontendClient, paginationConfig));
  }

  @BeforeMethod
  public void setUpTest() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
  }

  @Test
  public void succeedsToListAll() throws Throwable {
    int virtualNetworkNumber = 2;
    List<VirtualSubnet> expectedVirtualSubnets = createVirtualSubnets(virtualNetworkNumber);

    when(frontendClient.list(null, null, Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(expectedVirtualSubnets));

    Response response = listSubnets(Optional.absent(), Optional.of(1), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualSubnet> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualSubnet>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(virtualNetworkNumber));
    for (int i = 0; i < virtualNetworkNumber; ++i) {
      VirtualSubnet expectedVirtualSubnet = expectedVirtualSubnets.get(i);
      VirtualSubnet actualVirtualSubnet = virtualNetworks.getItems().get(i);
      assertThat(actualVirtualSubnet, is(expectedVirtualSubnet));

      String apiRoutePath = UriBuilder
          .fromPath(SubnetResourceRoutes.SUBNET_PATH)
          .build(expectedVirtualSubnet.getId())
          .toString();
      assertThat(actualVirtualSubnet.getSelfLink().endsWith(apiRoutePath), is(true));
      assertThat(new URI(actualVirtualSubnet.getSelfLink()).isAbsolute(), is(true));
    }
  }

  @Test(dataProvider = "listAllWithPageSize")
  public void succeedsToListAllWithPageSize(Optional<Integer> pageSize,
                                            List<VirtualSubnet> expectedVirtualSubnets) throws Throwable {
    when(frontendClient.list(null, null, Optional.absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(expectedVirtualSubnets, null, null));
    if (!expectedVirtualSubnets.isEmpty()) {
      when(frontendClient.list(null, null, Optional.absent(), Optional.of(1)))
          .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualSubnets.get(0)),
              UUID.randomUUID().toString(), null));
    }
    when(frontendClient.list(null, null, Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(expectedVirtualSubnets, null, null));
    when(frontendClient.list(null, null, Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = listSubnets(Optional.absent(), pageSize, Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualSubnet> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualSubnet>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(expectedVirtualSubnets.size()));
    for (int i = 0; i < virtualNetworks.getItems().size(); ++i) {
      VirtualSubnet expectedVirtualSubnet = expectedVirtualSubnets.get(i);
      VirtualSubnet actualVirtualSubnet = virtualNetworks.getItems().get(i);
      assertThat(actualVirtualSubnet, is(expectedVirtualSubnet));

      String apiRoutePath = UriBuilder
          .fromPath(SubnetResourceRoutes.SUBNET_PATH)
          .build(expectedVirtualSubnet.getId())
          .toString();
      assertThat(actualVirtualSubnet.getSelfLink().endsWith(apiRoutePath), is(true));
      assertThat(new URI(actualVirtualSubnet.getSelfLink()).isAbsolute(), is(true));
    }
  }

  @DataProvider(name = "listAllWithPageSize")
  private Object[][] getListAllWithPageSize() {
    int virtualNetworkNumber = 2;
    List<VirtualSubnet> expectedVirtualSubnets = createVirtualSubnets(virtualNetworkNumber);
    return new Object[][]{
        {
            Optional.absent(),
            expectedVirtualSubnets
        },
        {
            Optional.of(1),
            ImmutableList.of(expectedVirtualSubnets.get(0))
        },
        {
            Optional.of(2),
            expectedVirtualSubnets
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }

  @Test
  public void succeedsToListAllWithPageLink() throws Throwable {
    String pageLink = "randomPageLink";
    VirtualSubnet expectedVirtualSubnet = createVirtualSubnet("virtualNetwork");

    when(frontendClient.nextList(pageLink))
        .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualSubnet)));

    Response response = listSubnets(Optional.absent(), Optional.absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualSubnet> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualSubnet>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(1));

    VirtualSubnet actualVirtualSubnet = virtualNetworks.getItems().get(0);
    assertThat(actualVirtualSubnet, is(expectedVirtualSubnet));

    String apiRoutePath = UriBuilder
        .fromPath(SubnetResourceRoutes.SUBNET_PATH)
        .build(expectedVirtualSubnet.getId())
        .toString();
    assertThat(actualVirtualSubnet.getSelfLink().endsWith(apiRoutePath), is(true));
    assertThat(new URI(actualVirtualSubnet.getSelfLink()).isAbsolute(), is(true));
  }

  @Test
  public void succeedsToListByName() throws Throwable {
    VirtualSubnet expectedVirtualSubnet = createVirtualSubnet("virtualNetwork");

    when(frontendClient.list(null, null, Optional.of("virtualNetwork"), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(expectedVirtualSubnet)));

    Response response = listSubnets(Optional.of("virtualNetwork"), Optional.of(1), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<VirtualSubnet> virtualNetworks =
        response.readEntity(new GenericType<ResourceList<VirtualSubnet>>() {
        });
    assertThat(virtualNetworks.getItems().size(), is(1));

    VirtualSubnet actualVirtualSubnet = virtualNetworks.getItems().get(0);
    assertThat(actualVirtualSubnet, is(expectedVirtualSubnet));

    String apiRoutePath = UriBuilder
        .fromPath(SubnetResourceRoutes.SUBNET_PATH)
        .build(expectedVirtualSubnet.getId())
        .toString();
    assertThat(actualVirtualSubnet.getSelfLink().endsWith(apiRoutePath), is(true));
    assertThat(new URI(actualVirtualSubnet.getSelfLink()).isAbsolute(), is(true));
  }

  @Test
  public void failsToListAllWithException() throws Throwable {
    when(frontendClient.list(null, null, Optional.absent(), Optional.of(1)))
        .thenThrow(new ExternalException("failed"));
    assertThat(listSubnets(Optional.absent(), Optional.of(1), Optional.absent()).getStatus(),
        is(500));
  }

  @Test
  public void failsToListAllWithInvalidPageSize() throws Throwable {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = listSubnets(Optional.absent(), Optional.of(pageSize), Optional.absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void failsToListAllWithInvalidPageLink() throws Throwable {
    String pageLink = "randomPageLink";
    doThrow(new PageExpiredException(pageLink)).when(frontendClient).nextList(pageLink);

    Response response = listSubnets(Optional.absent(), Optional.absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  @Test
  public void failsToListByNameWithException() throws Throwable {
    when(frontendClient.list(null, null, Optional.of("virtualNetwork"), Optional.of(1)))
        .thenThrow(new ExternalException("failed"));
    assertThat(listSubnets(Optional.of("virtualNetwork"), Optional.of(1), Optional.absent()).getStatus(),
        is(500));
  }

  private Response listSubnets(Optional<String> name, Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(SubnetResourceRoutes.API);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  private List<VirtualSubnet> createVirtualSubnets(int virtualNetworkNumber) {
    List<VirtualSubnet> virtualSubnets = new ArrayList<>();
    for (int i = 0; i < virtualNetworkNumber; ++i) {
      virtualSubnets.add(createVirtualSubnet("virtualNetwork" + i));
    }
    return virtualSubnets;
  }

  private VirtualSubnet createVirtualSubnet(String name) {
    VirtualSubnet virtualSubnet = new VirtualSubnet();
    virtualSubnet.setId(UUID.randomUUID().toString());
    virtualSubnet.setName(name);
    return virtualSubnet;
  }

}
