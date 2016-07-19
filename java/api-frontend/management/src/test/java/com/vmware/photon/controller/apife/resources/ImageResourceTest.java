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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageSetting;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.ImageFeClient;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException.Type;
import com.vmware.photon.controller.apife.resources.image.ImageResource;
import com.vmware.photon.controller.apife.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.image.ImageResource}.
 */
public class ImageResourceTest extends ResourceTest {

  private String imageName = "imageName";

  private String imageId = "image1";

  private String imageRoutePath =
      UriBuilder.fromPath(ImageResourceRoutes.IMAGE_PATH).build(imageId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ImageFeClient imageFeClient;

  private Image testImage;

  private List<Image> testList;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ImageResource(imageFeClient));
  }

  @BeforeMethod
  public void setUp() {
    ImageSetting imageSetting = new ImageSetting();
    imageSetting.setName("propertyName1");
    imageSetting.setDefaultValue("propertyValue1");

    List<ImageSetting> imageSettings = new ArrayList<>();
    imageSettings.add(imageSetting);

    testImage = new Image();
    testImage.setId(imageId);
    testImage.setName(imageName);
    testImage.setSize(101L);
    testImage.setSettings(imageSettings);

    testList = new ArrayList<>();
    testList.add(testImage);
  }

  @Test
  public void testGetImage() throws ExternalException, URISyntaxException {
    when(imageFeClient.get(imageId)).thenReturn(testImage);

    Response response = client().target(imageRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    Image responseImage = response.readEntity(Image.class);

    assertThat(responseImage, is(testImage));
    assertThat(new URI(responseImage.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseImage.getSelfLink().endsWith(imageRoutePath), is(true));
  }

  @Test
  public void testGetNonExistingImage() throws ExternalException {
    when(imageFeClient.get("not-existed")).thenThrow(new ImageNotFoundException(Type.ID, "not-existed"));

    Response response = client().target("/images/not-existed").request().get();

    assertThat(response.getStatus(), is(404));

    verify(imageFeClient).get("not-existed");
  }

  @Test
  public void testDeleteImage() throws Throwable {
    Task task = new Task();
    task.setId(taskId);
    when(imageFeClient.delete(imageId)).thenReturn(task);

    Response response = client().target(imageRoutePath).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
