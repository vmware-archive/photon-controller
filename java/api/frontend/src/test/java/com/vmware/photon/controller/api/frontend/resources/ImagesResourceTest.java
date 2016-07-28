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

import com.vmware.photon.controller.api.frontend.clients.ImageFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.resources.image.ImagesResource;
import com.vmware.photon.controller.api.frontend.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ResourceList;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.core.Is;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.image.ImagesResource}.
 */
public class ImagesResourceTest extends ResourceTest {

  @Mock
  private ImageFeClient imageFeClient;

  private PaginationConfig paginationConfig = new PaginationConfig();
  private Image image1 = new Image();
  private Image image2 = new Image();

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ImagesResource(imageFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setUp() {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    image1.setId("img1");
    image1.setName("img1Name.vmdk");
    image1.setSize(101L);

    image2.setId("img2");
    image2.setName("img2Name.vmdk");
    image2.setSize(101L);
  }

  @Test
  public void testGetImagesPage() throws Exception {
    ResourceList<Image> expectedImagesPage = new ResourceList<>(ImmutableList.of(image1, image2),
        null, null);
    when(imageFeClient.getImagesPage(anyString())).thenReturn(expectedImagesPage);

    Response response = getImages(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Image> images = response.readEntity(
        new GenericType<ResourceList<Image>>() {
        }
    );

    assertThat(images.getItems().size(), is(expectedImagesPage.getItems().size()));

    for (int i = 0; i < images.getItems().size(); i++) {
      Image retrievedImage = images.getItems().get(i);
      assertThat(retrievedImage, is(expectedImagesPage.getItems().get(i)));
      assertThat(new URI(retrievedImage.getSelfLink()).isAbsolute(), is(true));

      String imageRoutePath = UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(retrievedImage.getId())
          .toString();
      assertThat(retrievedImage.getSelfLink().endsWith(imageRoutePath), is(true));
    }

    verifyPageLinks(images);
  }

  @Test(dataProvider = "pageSizes")
  public void testGetImages(Optional<Integer> pageSize, List<Image> expectedImages) throws Throwable {
    when(imageFeClient.list(Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.absent(), Optional.absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1), UUID.randomUUID().toString(), null));
    when(imageFeClient.list(Optional.absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = getImages(pageSize);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Image> result = response.readEntity(new GenericType<ResourceList<Image>>() {});
    assertThat(result.getItems().size(), is(expectedImages.size()));

    for (int i = 0; i < result.getItems().size(); i++) {
      Image retrievedImage = result.getItems().get(i);
      assertThat(retrievedImage, is(expectedImages.get(i)));
      assertThat(new URI(retrievedImage.getSelfLink()).isAbsolute(), is(true));

      String imageRoutePath = UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(retrievedImage.getId())
          .toString();
      assertThat(retrievedImage.getSelfLink().endsWith(imageRoutePath), is(true));
    }

    verifyPageLinks(result);
  }

  @Test
  public void testInvalidPageSize() {
    int pageSize = paginationConfig.getMaxPageSize() + 1;
    Response response = getImages(Optional.of(pageSize));
    assertThat(response.getStatus(), Is.is(400));

    String expectedErrorMsg = String.format("The page size '%d' is not between '1' and '%d'",
        pageSize, PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Is.is(ErrorCode.INVALID_PAGE_SIZE.getCode()));
    assertThat(errors.getMessage(), Is.is(expectedErrorMsg));
  }

  @Test
  public void testInvalidPageLink() throws ExternalException {
    String pageLink = "randomPageLink";
    doThrow(new PageExpiredException(pageLink)).when(imageFeClient).getImagesPage(pageLink);

    Response response = getImages(pageLink);
    assertThat(response.getStatus(), Is.is(Response.Status.NOT_FOUND.getStatusCode()));

    String expectedErrorMessage = "Page " + pageLink + " has expired";

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), Is.is(ErrorCode.PAGE_EXPIRED.getCode()));
    assertThat(errors.getMessage(), Is.is(expectedErrorMessage));
  }

  private Response getImages(Optional<Integer> pageSize) {
    String uri = ImageResourceRoutes.API;
    if (pageSize.isPresent()) {
      uri += "?pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getImages(String pageLink) {
    String uri = ImageResourceRoutes.API + "?pageLink=" + pageLink;
    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Image> resourceList) {
    String expectedPrefix = ImageResourceRoutes.API + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][]{
        {
            Optional.absent(),
            ImmutableList.of(image1, image2)
        },
        {
            Optional.of(1),
            ImmutableList.of(image1)
        },
        {
            Optional.of(2),
            ImmutableList.of(image1, image2)
        },
        {
            Optional.of(3),
            Collections.emptyList()
        }
    };
  }

}
