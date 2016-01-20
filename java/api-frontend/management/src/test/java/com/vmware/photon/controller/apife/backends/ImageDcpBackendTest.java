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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.steps.ImageUploadStepCmd;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.DcpClient;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Test {@link ImageBackend}.
 */
public class ImageDcpBackendTest {
  private static ApiFeDcpRestClient dcpClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeDcpRestClient apiFeDcpRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeDcpRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  private static ImageEntity prepareImageUpload(ImageBackend imageBackend, InputStream inputStream,
                                                String imageFileName, String imageName,
                                                ImageReplicationType replicationType)
      throws ExternalException {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, imageFileName, replicationType);
    assertThat(task.getSteps().size(), is(2));

    StepEntity step = task.getSteps().get(0);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.UPLOAD_IMAGE));
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);
    assertThat(image.getName(), is(imageName));
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getReplicationType(), is(replicationType));
    assertThat((InputStream) step.getTransientResource(ImageUploadStepCmd.INPUT_STREAM), is(inputStream));

    step = task.getSteps().get(1);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.REPLICATE_IMAGE));
    assertThat((ImageEntity) step.getTransientResourceEntities().get(0), is(image));
    assertThat(step.getTransientResource(ImageUploadStepCmd.INPUT_STREAM), nullValue());

    ImageEntity imageEntity = imageBackend.findById(task.getEntityId());
    assertThat(imageEntity.getName(), is(imageName));
    assertThat(imageEntity.getState(), is(ImageState.CREATING));
    assertThat(imageEntity.getReplicationType(), is(replicationType));

    assertThat(task.getToBeLockedEntityIds().size(), is(1));
    assertThat(task.getToBeLockedEntityIds().get(0), is(task.getEntityId()));

    return imageEntity;
  }

  private static String createImageDocument(DcpClient dcpClient,
                                            String imageName,
                                            ImageState imageState,
                                            Long imageSize) throws Throwable {
    return createImageDocument(dcpClient, imageName, imageState, imageSize, 10, 5);
  }

  private static String createImageDocument(DcpClient dcpClient,
                                            String imageName,
                                            ImageState imageState,
                                            Long imageSize,
                                            Integer totalDatastore,
                                            Integer replicatedDatastore) throws Throwable {
    ImageService.State imageServiceState = new ImageService.State();
    imageServiceState.name = imageName;
    imageServiceState.state = imageState;
    imageServiceState.replicationType = ImageReplicationType.EAGER;
    imageServiceState.size = imageSize;
    imageServiceState.totalDatastore = totalDatastore;
    imageServiceState.totalImageDatastore = 8;
    imageServiceState.replicatedDatastore = replicatedDatastore;
    imageServiceState.replicatedImageDatastore = 2;
    com.vmware.xenon.common.Operation result = dcpClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);
    ImageService.State createdState = result.getBody(ImageService.State.class);
    return ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);
  }


  @Test
  private void dummy() {
  }

  /**
   * Tests {@link ImageDcpBackend#deriveImage(ImageCreateSpec,
   * ImageEntity)}.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class DeriveImageUploadTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testSuccess() throws Throwable {
      ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
      imageCreateSpec.setName("i1");
      imageCreateSpec.setReplicationType(ImageReplicationType.EAGER);

      ImageEntity originalImage = new ImageEntity();
      originalImage.setSize(100L);
      ImageSettingsEntity imageSettingsEntity = new ImageSettingsEntity();
      imageSettingsEntity.setImage(originalImage);
      imageSettingsEntity.setName("n");
      imageSettingsEntity.setDefaultValue("v");
      originalImage.getImageSettings().add(imageSettingsEntity);

      String imageId = imageBackend.deriveImage(imageCreateSpec, originalImage).getId();
      ImageEntity image = imageBackend.findById(imageId);
      assertThat(image.getName(), is(imageCreateSpec.getName()));
      assertThat(image.getReplicationType(), is(imageCreateSpec.getReplicationType()));
      assertThat(image.getState(), is(ImageState.CREATING));
      assertThat(image.getSize(), is(100L));
      assertThat(image.getImageSettingsMap(), is((Map<String, String>) ImmutableMap.of("n", "v")));
    }
  }

  /**
   * Tests for creating a image.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class PrepareImageUploadTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareImageUploadEager() throws ExternalException {
      imageName = UUID.randomUUID().toString();
      prepareImageUpload(imageBackend, inputStream, imageName, imageName, ImageReplicationType.EAGER);
    }

    @Test
    public void testPrepareImageUploadOnDemand() throws ExternalException {
      imageName = UUID.randomUUID().toString();
      prepareImageUpload(imageBackend, inputStream, imageName, imageName, ImageReplicationType.ON_DEMAND);
    }

    @Test(dataProvider = "ImageFileNames")
    public void testPrepareImageUploadImageFileNames(String imageFileName) throws ExternalException {
      prepareImageUpload(imageBackend, inputStream, imageFileName, imageName, ImageReplicationType.ON_DEMAND);
    }

    @DataProvider(name = "ImageFileNames")
    public Object[][] getImageNames() {
      imageName = UUID.randomUUID().toString();
      return new Object[][]{
          {imageName},
          {"/tmp/" + imageName},
          {"tmp/" + imageName}
      };
    }

    @Test(expectedExceptions = ImageUploadException.class,
        expectedExceptionsMessageRegExp = "Image file name cannot be blank.")
    public void testPrepareImageUploadBlankFileName() throws Throwable {
      imageBackend.prepareImageUpload(inputStream, "", ImageReplicationType.ON_DEMAND);
    }

    @Test
    public void testUploadingImageWithDuplicatedName() throws ExternalException {
      int currentCountOfImages = imageBackend.getAll().size();
      String testImage = UUID.randomUUID().toString();
      imageBackend.prepareImageUpload(inputStream, testImage, ImageReplicationType.ON_DEMAND);
      imageBackend.prepareImageUpload(inputStream, testImage, ImageReplicationType.ON_DEMAND);

      assertThat(imageBackend.getAll().size(), is(currentCountOfImages + 2));
    }
  }

  /**
   * Tests for deleting an image.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ImageDeleteTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareImageDelete() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(dcpClient, imageName, ImageState.READY, 1L);

      TaskEntity taskDelete = imageBackend.prepareImageDelete(id);
      assertThat(taskDelete.getSteps().size(), is(2));
      assertThat(taskDelete.getSteps().get(0).getOperation(), is(Operation.DELETE_IMAGE));
      assertThat(taskDelete.getSteps().get(1).getOperation(), is(Operation.DELETE_IMAGE_REPLICAS));
    }

    @Test(expectedExceptions = InvalidImageStateException.class)
    public void testPrepareImageDeleteInPendingDelete() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(dcpClient, imageName, ImageState.PENDING_DELETE, 1L);
      imageBackend.prepareImageDelete(id);
    }

    @Test(expectedExceptions = ImageNotFoundException.class)
    public void testDeletingAnNonExistingImage() throws Throwable {
      imageBackend.prepareImageDelete("non-existing-id");
    }

    @Test
    public void testTombstone() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(dcpClient, imageName, ImageState.READY, 1L);
      ImageEntity imageEntity = imageBackend.findById(id);
      assertThat(imageBackend.findById(imageEntity.getId()), notNullValue());

      imageBackend.tombstone(imageEntity);
      try {
        imageBackend.findById(imageEntity.getId());
        fail("should have failed with ImageNotFoundException.");
      } catch (ImageNotFoundException e) {
      }
    }
  }

  /**
   * Tests for updating an Image.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ImageUpdateTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeDcpRestClient apiFeDcpRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeDcpRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testUpdateSettings() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String imageId = createImageDocument(dcpClient, imageName, ImageState.READY, 1L);

      ImageEntity imageEntity = imageBackend.findById(imageId);

      Map<String, String> imageSettings = new HashMap<>();
      imageSettings.put("property-1", "value-1");
      imageSettings.put("property-2", null);

      imageBackend.updateSettings(imageEntity, imageSettings);

      imageEntity = imageBackend.findById(imageId);

      assertThat(imageEntity.getImageSettings().size(), is(2));

      ImageSettingsEntity settings1 = imageEntity.getImageSettings().get(0);
      assertThat(settings1.getImage().getId(), is(imageId));
      assertThat(settings1.getName(), is("property-1"));
      assertThat(settings1.getDefaultValue(), is("value-1"));

      ImageSettingsEntity settings2 = imageEntity.getImageSettings().get(1);
      assertThat(settings2.getImage().getId(), is(imageId));
      assertThat(settings2.getName(), is("property-2"));
      assertThat(settings2.getDefaultValue(), is(""));
    }

    @Test
    public void testUpdateSize() throws Throwable {
      imageName = UUID.randomUUID().toString();
      Long originalImageSize = 1L;
      Long newImageSize = originalImageSize + 1L;
      String imageId = createImageDocument(dcpClient, imageName, ImageState.READY, originalImageSize);
      ImageEntity imageEntity = imageBackend.findById(imageId);
      assertThat(imageEntity.getTotalDatastore(), is(10));
      assertThat(imageEntity.getTotalImageDatastore(), is(8));
      assertThat(imageEntity.getReplicatedDatastore(), is(5));

      imageBackend.updateSize(imageEntity, newImageSize);
      imageEntity = imageBackend.findById(imageId);
      assertThat(imageEntity.getSize(), is(newImageSize));
    }

    @Test
    public void testUpdateImageDatastore() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String imageDatastoreId = "image-datastore-id";
      String imageId = createImageDocument(dcpClient, imageName, ImageState.READY, 1L);
      ImageEntity imageEntity = imageBackend.findById(imageId);

      DatastoreService.State datastoreState = new DatastoreService.State();
      datastoreState.name = "image-datastore-name";
      datastoreState.id = imageDatastoreId;
      datastoreState.documentSelfLink = imageDatastoreId;
      datastoreState.type = "type";
      datastoreState.isImageDatastore = true;

      dcpClient.post(DatastoreServiceFactory.SELF_LINK, datastoreState);

      imageBackend.updateImageDatastore(imageEntity.getId(), "image-datastore-name");

      final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("imageId", imageEntity.getId());
      List<ImageReplicationService.State> results = dcpClient.queryDocuments(ImageReplicationService.State.class,
          termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));
    }

    @Test
    public void testUpdateImageDatastoreTwice() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String imageDatastoreId = "image-datastore-id";
      String imageId = createImageDocument(dcpClient, imageName, ImageState.READY, 1L);
      ImageEntity imageEntity = imageBackend.findById(imageId);

      DatastoreService.State datastoreState = new DatastoreService.State();
      datastoreState.name = "image-datastore-name";
      datastoreState.id = imageDatastoreId;
      datastoreState.documentSelfLink = imageDatastoreId;
      datastoreState.type = "type";
      datastoreState.isImageDatastore = true;
      dcpClient.post(DatastoreServiceFactory.SELF_LINK, datastoreState);

      imageBackend.updateImageDatastore(imageEntity.getId(), "image-datastore-name");

      final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("imageId", imageEntity.getId());
      List<ImageReplicationService.State> results = dcpClient.queryDocuments(ImageReplicationService.State.class,
          termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));

      imageBackend.updateImageDatastore(imageEntity.getId(), "image-datastore-name");
      results = dcpClient.queryDocuments(ImageReplicationService.State.class,
          termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));
    }

    @Test
    public void testUpdateReplicationStatus() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String imageId = createImageDocument(dcpClient, imageName, ImageState.CREATING, 1L, 10, 10);
      ImageEntity imageEntity = imageBackend.findById(imageId);
      imageBackend.updateReplicationStatus(imageEntity.getId());

      ImageService.State savedState = dcpClient.get(ImageServiceFactory.SELF_LINK + "/" + imageId)
          .getBody(ImageService.State.class);
      assertThat(savedState.state, is(ImageState.READY));
    }

    @Test
    public void testUpdateReplicationDocumentNotFound() throws Throwable {
      try {
        imageBackend.updateReplicationStatus("invalid-image");
      } catch (ImageNotFoundException e) {
        assertThat(e.getMessage(), is("Image id 'invalid-image' not found"));
      }
    }
  }
}
