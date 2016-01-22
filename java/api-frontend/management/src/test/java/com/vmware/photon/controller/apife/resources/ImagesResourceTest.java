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

import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.ImageFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link ImagesResource}.
 */
public class ImagesResourceTest extends ResourceTest {

  private String imageName = "imageName.vmdk";

  private String imageId = "image1";

  private String imageRoutePath =
      UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(imageId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();


  @Mock
  private ImageFeClient imageFeClient;

  private String image1Link = "";
  private String image2Link = "";
  private PaginationConfig paginationConfig = new PaginationConfig();
  private Image image1 = new Image();
  private Image image2 = new Image();

  @Mock
  private HttpServletRequest httpServletRequest;

  private Image testImage;

  private List<Image> testList;

  private ResourceList<Image> testImageList;

  @Override
  protected void setUpResources() throws Exception {
    addProvider(httpServletRequest);
    addResource(new ImagesResource(imageFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setUp() {
    paginationConfig.setDefaultPageSize(10);
    paginationConfig.setMaxPageSize(100);

    testImage = new Image();
    testImage.setId(imageId);
    testImage.setName(imageName);
    testImage.setSize(101L);

    testList = new ArrayList<>();
    testList.add(testImage);

    testImageList = new ResourceList<>(testList);

    image1.setId("img1");
    image1.setName("img1Name.vmdk");
    image1.setSize(101L);

    image2.setId("img2");
    image2.setName("img2Name.vmdk");
    image2.setSize(101L);

    image1Link = UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(image1.getId()).toString();
    image2Link = UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(image2.getId()).toString();
    image1.setSelfLink(image1Link);
    image2.setSelfLink(image2Link);

  }

  @Test
  public void testGetAllImages() throws URISyntaxException, ExternalException {
    when(imageFeClient.list(any(Optional.class))).thenReturn(testImageList);

    Response response = client().target(ImageResourceRoutes.API).request().get();
    assertThat(response.getStatus(), is(200));

    ResourceList<Image> list = response.readEntity(
        new GenericType<ResourceList<Image>>() {
        }
    );

    assertThat(list.getItems().size(), is(1));
    Image image = list.getItems().get(0);
    assertThat(image, is(testImage));
    assertThat(new URI(image.getSelfLink()).isAbsolute(), is(true));
    assertThat(image.getSelfLink().endsWith(imageRoutePath), is(true));
  }

  @Test
  public void testGetImagesPage() throws Exception {
    ResourceList<Image> expectedImagesPage = new ResourceList<>(ImmutableList.of(image1, image2),
        null, null);
    when(imageFeClient.getImagesPage(anyString())).thenReturn(expectedImagesPage);

    List<String> expectedSelfLinks = ImmutableList.of(image1Link, image2Link);

    Response response = getImages(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Image> images = response.readEntity(
        new GenericType<ResourceList<Image>>() {
        }
    );

    assertThat(images.getItems().size(), is(expectedImagesPage.getItems().size()));

    for (int i = 0; i < images.getItems().size(); i++) {
      assertThat(new URI(images.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(images.getItems().get(i), is(expectedImagesPage.getItems().get(i)));
      assertThat(images.getItems().get(i).getSelfLink().endsWith(expectedSelfLinks.get(i)), is(true));
    }

    verifyPageLinks(images);
  }

  @Test(dataProvider = "pageSizes")
  public void testGetImages(Optional<Integer> pageSize, List<Image> expectedImages) throws Throwable {
    when(imageFeClient.list(Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.absent()))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1), null, null));
    when(imageFeClient.list(Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(image1, image2), null, null));
    when(imageFeClient.list(Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = getImages(pageSize);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    ResourceList<Image> result = response.readEntity(new GenericType<ResourceList<Image>>() {});
    assertThat(result.getItems().size(), is(expectedImages.size()));

    for (int i = 0; i < result.getItems().size(); i++) {
      assertThat(new URI(result.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(result.getItems().get(i), is(expectedImages.get(i)));
    }

    verifyPageLinks(result);
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
