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

import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.PortGroup;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.clients.PortGroupFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.portgroup.PortGroupsResource;
import com.vmware.photon.controller.apife.resources.routes.PortGroupResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.portgroup.PortGroupsResource}.
 */
public class PortGroupsResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(PortGroupsResourceTest.class);

  @Mock
  private PortGroupFeClient portGroupFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();

  private PortGroup portGroup1 = new PortGroup();
  private PortGroup portGroup2 = new PortGroup();

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);

    addResource(new PortGroupsResource(portGroupFeClient, paginationConfig));

    portGroup1.setId("network1");
    portGroup1.setName("VMNetwork");
    portGroup1.setUsageTags(Arrays.asList(UsageTag.CLOUD));

    portGroup2.setId("network2");
    portGroup2.setName("VMNetwork");
    portGroup2.setUsageTags(Arrays.asList(UsageTag.MGMT));
  }

  @Test
  public void testGetEmptyPortGroupList() {
    ResourceList<PortGroup> resourceList = new ResourceList<>(new ArrayList<PortGroup>());
    doReturn(resourceList).when(portGroupFeClient).find(Optional.<String>absent(), Optional.<UsageTag>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    Response clientResponse = client()
        .target(PortGroupResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<PortGroup> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<PortGroup>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(0));
  }

  @Test(dataProvider = "pageSizes")
  public void testGetPortGroups(Optional<Integer> pageSize, List<PortGroup> expectedPortGroups) throws Exception {
    when(portGroupFeClient.find(Optional.absent(), Optional.absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(portGroup1, portGroup2)));
    when(portGroupFeClient.find(Optional.absent(), Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(portGroup1), UUID.randomUUID().toString(), null));
    when(portGroupFeClient.find(Optional.absent(), Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(portGroup1, portGroup2)));
    when(portGroupFeClient.find(Optional.absent(), Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList()));

    WebTarget resource = client().target(PortGroupResourceRoutes.API);
    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    Response response = resource.request(MediaType.APPLICATION_JSON).get();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<PortGroup> portGroups = response.readEntity(
        new GenericType<ResourceList<PortGroup>>() {
        }
    );

    assertThat(portGroups.getItems().size(), is(expectedPortGroups.size()));

    for (int i = 0; i < portGroups.getItems().size(); ++i) {
      PortGroup portGroup = portGroups.getItems().get(i);
      assertThat(portGroup, is(expectedPortGroups.get(i)));
      assertThat(new URI(portGroup.getSelfLink()).isAbsolute(), is(true));
      assertThat
          (portGroup.getSelfLink().endsWith(UriBuilder.fromPath(PortGroupResourceRoutes.PORT_GROUP_PATH)
                  .build(expectedPortGroups.get(i).getId()).toString()),
              is(true));
    }

    verifyPageLinks(portGroups);
  }

  @Test
  public void testInvalidPageSize() throws Exception {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = client().target(PortGroupResourceRoutes.API).queryParam("pageSize", pageSize).request().get();
    assertThat(response.getStatus(), Matchers.is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Matchers.is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), Matchers.is(expectedErrorMsg));
  }

  @Test
  public void testGetPortGroupListByName() throws URISyntaxException {
    List<PortGroup> portGroupList = new ArrayList<>();
    portGroupList.add(portGroup1);

    ResourceList<PortGroup> resourceList = new ResourceList<>(portGroupList);
    doReturn(resourceList).when(portGroupFeClient).find(Optional.of("P1"), Optional.of(UsageTag.CLOUD),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    Response clientResponse = client()
        .target(PortGroupResourceRoutes.API + "?name=P1&usageTag=CLOUD")
        .request(MediaType.APPLICATION_JSON)
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<PortGroup> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<PortGroup>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(1));

    PortGroup retrievedPortGroup = retrievedResources.getItems().get(0);
    assertThat(retrievedPortGroup, is(portGroup1));

    assertThat(new URI(retrievedPortGroup.getSelfLink()).isAbsolute(), is(true));
    assertThat(retrievedPortGroup.getSelfLink()
        .endsWith(UriBuilder.fromPath(PortGroupResourceRoutes.PORT_GROUP_PATH).build("network1").toString()), is(true));
  }

  @Test
  public void testGetPortGroupsPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<>(
        ImmutableList.of(portGroup1), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .when(portGroupFeClient)
        .getPortGroupsPage(pageLink);

    Response response = client().target(PortGroupResourceRoutes.API).queryParam("pageLink", pageLink).request().get();
    assertThat(response.getStatus(), Matchers.is(Response.Status.OK.getStatusCode()));

    ResourceList<PortGroup> portGroups = response.readEntity(
        new GenericType<ResourceList<PortGroup>>() {
        }
    );
    assertThat(portGroups.getItems().size(), is(1));

    PortGroup portGroup = portGroups.getItems().get(0);
    assertThat(portGroup, is(portGroup1));

    String portGroupRoutePath = UriBuilder.fromPath(PortGroupResourceRoutes.PORT_GROUP_PATH).build(portGroup1.getId())
        .toString();
    assertThat(portGroup.getSelfLink().endsWith(portGroupRoutePath), is(true));
    assertThat(new URI(portGroup.getSelfLink()).isAbsolute(), is(true));

    verifyPageLinks(portGroups);
  }

  @Test
  public void testInvalidPortGroupsPageLink() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(portGroupFeClient).getPortGroupsPage(pageLink);

    Response response = client().target(PortGroupResourceRoutes.API).queryParam("pageLink", pageLink).request().get();
    assertThat(response.getStatus(), Matchers.is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Matchers.is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), Matchers.is(expectedErrorMessage));
  }

  private void verifyPageLinks(ResourceList<PortGroup> resourceList) {
    String expectedPrefix = UriBuilder.fromPath(PortGroupResourceRoutes.API).build().toString() + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), Matchers.is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSizes() {
    return new Object[][] {
        {
            Optional.absent(),
            ImmutableList.of(portGroup1, portGroup2)
        },
        {
            Optional.of(1),
            ImmutableList.of(portGroup1)
        },
        {
            Optional.of(2),
            ImmutableList.of(portGroup1, portGroup2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }

    };
  }
}
