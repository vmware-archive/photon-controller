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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImagesApi}.
 */
public class ImagesApiTest extends ApiTestBase {

  @Test
  public void testUploadImage() throws IOException {
    File temp = File.createTempFile("temp-image", ".vmdk");
    (new File(temp.getAbsolutePath())).deleteOnExit();

    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);
    ImagesApi imagesApi = new ImagesApi(this.restClient);

    Task task = imagesApi.uploadImage(temp.getAbsolutePath());
    assertEquals(task, responseTask);
  }

  @Test
  public void testGetImage() throws IOException {
    Image image = new Image();
    image.setId("image1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(image);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);

    Image response = imagesApi.getImage("image1");
    assertEquals(response, image);
  }

  @Test
  public void testGetImageAsync() throws IOException, InterruptedException {
    final Image image = new Image();
    image.setId("image1");

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(image);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    imagesApi.getImageAsync(image.getId(), new FutureCallback<Image>() {
      @Override
      public void onSuccess(@Nullable Image result) {
        assertEquals(result, image);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testGetAllImages() throws IOException {
    Image image1 = new Image();
    image1.setId("image1");

    Image image2 = new Image();
    image2.setId("image2");

    ResourceList<Image> imageResourceList = new ResourceList<>(Arrays.asList(image1, image2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(imageResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);

    ResourceList<Image> response = imagesApi.getImages();
    assertEquals(response.getItems().size(), imageResourceList.getItems().size());
    assertTrue(response.getItems().containsAll(imageResourceList.getItems()));
  }

  @Test
  public void testGetAllImagesForPagination() throws IOException {
    Image image1 = new Image();
    image1.setId("image1");

    Image image2 = new Image();
    image2.setId("image2");

    Image image3 = new Image();
    image3.setId("image3");

    String nextPageLink = "nextPageLink";

    ResourceList<Image> imageResourceList = new ResourceList<>(Arrays.asList(image1, image2), nextPageLink, null);
    ResourceList<Image> imageResourceListNextPage = new ResourceList<>(Arrays.asList(image3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(imageResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(imageResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);
    ResourceList<Image> response = imagesApi.getImages();
    assertEquals(response.getItems().size(), imageResourceList.getItems().size() + imageResourceListNextPage
        .getItems().size());
    assertTrue(response.getItems().containsAll(imageResourceList.getItems()));
    assertTrue(response.getItems().containsAll(imageResourceListNextPage.getItems()));
  }

  @Test
  public void testGetAllImagesAsync() throws IOException, InterruptedException {
    Image image1 = new Image();
    image1.setId("image1");

    Image image2 = new Image();
    image2.setId("image2");

    final ResourceList<Image> imageResourceList = new ResourceList<>(Arrays.asList(image1, image2));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(imageResourceList);

    setupMocks(serializedTask, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    imagesApi.getImagesAsync(new FutureCallback<ResourceList<Image>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Image> result) {
        assertEquals(result.getItems(), imageResourceList.getItems());
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));

  }

  @Test
  public void testGetAllImagesAsyncForPagination() throws IOException, InterruptedException {
    Image image1 = new Image();
    image1.setId("image1");

    Image image2 = new Image();
    image2.setId("image2");

    Image image3 = new Image();
    image3.setId("image3");

    String nextPageLink = "nextPageLink";

    final ResourceList<Image> imageResourceList = new ResourceList<>(Arrays.asList(image1, image2), nextPageLink, null);
    final ResourceList<Image> imageResourceListNextPage = new ResourceList<>(Arrays.asList(image3));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(imageResourceList);
    String serializedTaskNextPage = mapper.writeValueAsString(imageResourceListNextPage);

    setupMocksForPagination(serializedTask, serializedTaskNextPage, nextPageLink, HttpStatus.SC_OK);

    ImagesApi imagesApi = new ImagesApi(this.restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    imagesApi.getImagesAsync(new FutureCallback<ResourceList<Image>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Image> result) {
        assertEquals(result.getItems().size(), imageResourceList.getItems().size() + imageResourceListNextPage
            .getItems().size());
        assertTrue(result.getItems().containsAll(imageResourceList.getItems()));
        assertTrue(result.getItems().containsAll(imageResourceListNextPage.getItems()));
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }

  @Test
  public void testDeleteImage() throws IOException {
    Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ImagesApi imagesApi = new ImagesApi(this.restClient);

    Task task = imagesApi.delete("foo");
    assertEquals(task, responseTask);
  }

  @Test
  public void testDeleteImageAsync() throws IOException, InterruptedException {
    final Task responseTask = new Task();
    responseTask.setId("12345");
    responseTask.setState("QUEUED");
    responseTask.setQueuedTime(Date.from(Instant.now()));

    ObjectMapper mapper = new ObjectMapper();
    String serializedTask = mapper.writeValueAsString(responseTask);

    setupMocks(serializedTask, HttpStatus.SC_CREATED);

    ImagesApi imagesApi = new ImagesApi(this.restClient);
    final CountDownLatch latch = new CountDownLatch(1);

    imagesApi.deleteAsync("foo", new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        assertEquals(result, responseTask);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }
}
