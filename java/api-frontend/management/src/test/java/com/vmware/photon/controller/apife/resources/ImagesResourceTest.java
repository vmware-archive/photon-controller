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
import com.vmware.photon.controller.apife.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

  @Mock
  private HttpServletRequest httpServletRequest;

  private Image testImage;

  private List<Image> testList;

  private ResourceList<Image> testImageList;

  @Override
  protected void setUpResources() throws Exception {
    addProvider(httpServletRequest);
    addResource(new ImagesResource(imageFeClient));
  }

  @BeforeMethod
  public void setUp() {
    testImage = new Image();
    testImage.setId(imageId);
    testImage.setName(imageName);
    testImage.setSize(101L);

    testList = new ArrayList<>();
    testList.add(testImage);

    testImageList = new ResourceList<>(testList);
  }

  @Test
  public void testGetAllImages() throws URISyntaxException, ExternalException {
    when(imageFeClient.list()).thenReturn(testImageList);

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

}
