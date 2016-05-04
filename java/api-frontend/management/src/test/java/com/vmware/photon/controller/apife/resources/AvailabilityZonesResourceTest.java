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
import com.vmware.photon.controller.api.AvailabilityZone;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.clients.AvailabilityZoneFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.availabilityzone.AvailabilityZonesResource;
import com.vmware.photon.controller.apife.resources.routes.AvailabilityZonesResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.availabilityzone.AvailabilityZonesResource}.
 */
public class AvailabilityZonesResourceTest extends ResourceTest {
  @Mock
  private AvailabilityZoneFeClient availabilityZoneFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();
  private AvailabilityZone az1 = new AvailabilityZone();
  private AvailabilityZone az2 = new AvailabilityZone();

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    AvailabilityZonesResource resource = new AvailabilityZonesResource(availabilityZoneFeClient, paginationConfig);
    addResource(resource);

    az1.setId("az1");
    az2.setId("sz2");
  }

  @Test(dataProvider = "pageSizes")
  public void testList(Optional<Integer> pageSize,
                       List<AvailabilityZone> expectedAvailabilityZones) throws Exception {

    doReturn(new ResourceList<>(ImmutableList.of(az1, az2)))
        .when(availabilityZoneFeClient)
        .list(Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(ImmutableList.of(az1), UUID.randomUUID().toString(), null))
        .when(availabilityZoneFeClient)
        .list(Optional.of(1));
    doReturn(new ResourceList<>(ImmutableList.of(az1, az2)))
        .when(availabilityZoneFeClient)
        .list(Optional.of(2));
    doReturn(new ResourceList<>(Collections.emptyList()))
        .when(availabilityZoneFeClient)
        .list(Optional.of(3));

    Response response = getAvailabilityZones(pageSize, Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<AvailabilityZone> result = response.readEntity(new GenericType<ResourceList<AvailabilityZone>>() {});
    assertThat(result.getItems().size(), is(expectedAvailabilityZones.size()));

    for (int i = 0; i < result.getItems().size(); i++) {
      assertThat(new URI(result.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(result.getItems().get(i).getId(), is(expectedAvailabilityZones.get(i).getId()));

      String azRoutePath = UriBuilder.fromPath(AvailabilityZonesResourceRoutes.AVAILABILITYZONE_PATH)
          .build(expectedAvailabilityZones.get(i).getId()).toString();
      assertThat(result.getItems().get(i).getSelfLink().endsWith(azRoutePath), is(true));
    }

    verifyPageLinks(result);
  }

  @Test
  public void testInvalidPageSize() throws ExternalException {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getAvailabilityZones(Optional.of(pageSize), Optional.<String>absent());
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMsg));
  }

  @Test
  public void testListPage() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    PowerMockito.doReturn(new ResourceList<>(ImmutableList.of(az1), UUID.randomUUID().toString(),
        UUID.randomUUID().toString()))
        .when(availabilityZoneFeClient).listPage(pageLink);

    Response response = getAvailabilityZones(Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<AvailabilityZone> availabilityZoneResourceList = response.readEntity(
        new GenericType<ResourceList<AvailabilityZone>>() {
        }
    );
    assertThat(availabilityZoneResourceList.getItems().size(), is(1));

    AvailabilityZone az = availabilityZoneResourceList.getItems().get(0);
    assertThat(az.getId(), is(az1.getId()));
    assertThat(new URI(az.getSelfLink()).isAbsolute(), is(true));
    assertThat(az.getSelfLink().endsWith(UriBuilder.fromPath(AvailabilityZonesResourceRoutes.AVAILABILITYZONE_PATH)
        .build(az.getId()).toString()), is(true));
  }

  @Test
  public void testInvalidPageLink() throws Exception {
    String pageLink = UUID.randomUUID().toString();
    doThrow(new PageExpiredException(pageLink)).when(availabilityZoneFeClient).listPage(pageLink);

    Response response = getAvailabilityZones(Optional.<Integer>absent(), Optional.of(pageLink));
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), is(expectedErrorMessage));
  }

  private Response getAvailabilityZones(Optional<Integer> pageSize, Optional<String> pageLink) {
    WebTarget resource = client().target(AvailabilityZonesResourceRoutes.API);

    if (pageSize.isPresent()) {
      resource = resource.queryParam("pageSize", pageSize.get());
    }

    if (pageLink.isPresent()) {
      resource = resource.queryParam("pageLink", pageLink.get());
    }

    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<AvailabilityZone> resourceList) {
    String expectedPrefix = AvailabilityZonesResourceRoutes.API + "?pageLink=";

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
            ImmutableList.of(az1, az2)
        },
        {
            Optional.of(1),
            ImmutableList.of(az1)
        },
        {
            Optional.of(2),
            ImmutableList.of(az1, az2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }
}
