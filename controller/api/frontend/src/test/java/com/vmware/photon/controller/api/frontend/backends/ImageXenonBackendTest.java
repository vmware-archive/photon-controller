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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.TestModule;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.commands.steps.ImageUploadStepCmd;
import com.vmware.photon.controller.api.frontend.entities.ImageEntity;
import com.vmware.photon.controller.api.frontend.entities.ImageSettingsEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageCreateSpec;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.resource.gen.ImageReplication;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.commons.collections.CollectionUtils;
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

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Test {@link ImageBackend}.
 */
public class ImageXenonBackendTest {
  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
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
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  private static ImageEntity prepareImageUpload(ImageBackend imageBackend, InputStream inputStream,
                                                String imageFileName, String imageName,
                                                ImageReplication replicationType)
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

    assertThat(task.getToBeLockedEntities().size(), is(1));
    assertThat(task.getToBeLockedEntities().get(0).getId(), is(task.getEntityId()));
    assertThat(task.getToBeLockedEntities().get(0).getKind(), is(Image.KIND));

    return imageEntity;
  }

  private static String createImageDocument(
      ApiFeXenonRestClient xenonClient,
                                            String imageName,
                                            ImageState imageState,
                                            Long imageSize,
                                            int totalDatastore,
                                            int totalImageDatastore,
                                            int replicatedDatastore,
                                            int replicatedImageDatastore) throws Throwable {
    ImageService.State imageServiceState = new ImageService.State();
    imageServiceState.name = imageName;
    imageServiceState.state = imageState;
    imageServiceState.replicationType = ImageReplication.EAGER;
    imageServiceState.size = imageSize;
    imageServiceState.totalDatastore = totalDatastore;
    imageServiceState.totalImageDatastore = totalImageDatastore;
    imageServiceState.replicatedDatastore = replicatedDatastore;
    imageServiceState.replicatedImageDatastore = replicatedImageDatastore;
    com.vmware.xenon.common.Operation result = xenonClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);
    ImageService.State createdState = result.getBody(ImageService.State.class);
    return ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);
  }

  private static void createImageToImageDatastoreMappingService(String imageId, String imageDatastoreId) {
    ImageToImageDatastoreMappingService.State state = new ImageToImageDatastoreMappingService.State();
    state.imageId = imageId;
    state.imageDatastoreId = imageDatastoreId;
    state.documentSelfLink = imageId + "_" + imageDatastoreId;

    xenonClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, state);
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link ImageXenonBackend#deriveImage(ImageCreateSpec,
   * ImageEntity)}.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeriveImageUploadTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
      imageCreateSpec.setReplicationType(ImageReplication.EAGER);

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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareImageUploadTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
      prepareImageUpload(imageBackend, inputStream, imageName, imageName, ImageReplication.EAGER);
    }

    @Test
    public void testPrepareImageUploadOnDemand() throws ExternalException {
      imageName = UUID.randomUUID().toString();
      prepareImageUpload(imageBackend, inputStream, imageName, imageName, ImageReplication.ON_DEMAND);
    }

    @Test(dataProvider = "ImageFileNames")
    public void testPrepareImageUploadImageFileNames(String imageFileName) throws ExternalException {
      prepareImageUpload(imageBackend, inputStream, imageFileName, imageName, ImageReplication.ON_DEMAND);
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
      imageBackend.prepareImageUpload(inputStream, "", ImageReplication.ON_DEMAND);
    }

    @Test
    public void testUploadingImageWithDuplicatedName() throws ExternalException {
      int currentCountOfImages = imageBackend.getAll(Optional.absent()).getItems().size();
      String testImage = UUID.randomUUID().toString();
      imageBackend.prepareImageUpload(inputStream, testImage, ImageReplication.ON_DEMAND);
      imageBackend.prepareImageUpload(inputStream, testImage, ImageReplication.ON_DEMAND);

      assertThat(imageBackend.getAll(Optional.absent()).getItems().size(), is(currentCountOfImages + 2));
    }
  }

  /**
   * Tests for get an image/images.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ImageQueryTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testImageFindById() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);

      ImageEntity image = imageBackend.findById(id);
      assertThat(image.getId(), is(id));
      assertThat(image.getName(), is(imageName));
      assertThat(image.getState(), is(ImageState.READY));
      assertThat(image.getReplicationType(), is(ImageReplication.EAGER));
      assertThat(image.getSize(), is(1L));
      assertThat(image.getTotalDatastore(), is(10));
      assertThat(image.getTotalImageDatastore(), is(8));
      assertThat(image.getReplicatedDatastore(), is(5));
      assertThat(image.getReplicatedImageDatastore(), is(2));
    }

    @Test(expectedExceptions = ImageNotFoundException.class)
    public void testFindAnNonExistingImage() throws Throwable {
      imageBackend.findById("non-existing-id");
    }

    @Test
    public void testFilter() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);
      ResourceList<Image> imageResourceList = imageBackend.filter(Optional.of(imageName), Optional.absent());
      List<Image> images = imageResourceList.getItems();
      assertThat(images.size(), is(1));
      assertThat(images.get(0).getId(), is(id));
      assertThat(images.get(0).getName(), is(imageName));
    }
  }

  /**
   * Tests for deleting an image.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ImageDeleteTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
      String id = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);

      TaskEntity taskDelete = imageBackend.prepareImageDelete(id);
      assertThat(taskDelete.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(taskDelete.getSteps().size(), is(0));
    }

    @Test(expectedExceptions = InvalidImageStateException.class)
    public void testPrepareImageDeleteInPendingDelete() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(xenonClient, imageName, ImageState.PENDING_DELETE, 1L, 10, 8, 5, 2);
      imageBackend.prepareImageDelete(id);
    }

    @Test(expectedExceptions = ImageNotFoundException.class)
    public void testDeletingAnNonExistingImage() throws Throwable {
      imageBackend.prepareImageDelete("non-existing-id");
    }

    @Test
    public void testTombstone() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String id = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ImageUpdateTest {

    private static String imageName;

    @Mock
    private InputStream inputStream;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
      String imageId = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);

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
      String imageId = createImageDocument(xenonClient, imageName, ImageState.READY, originalImageSize, 10, 8, 5, 2);
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
      String imageId = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);
      ImageEntity imageEntity = imageBackend.findById(imageId);

      DatastoreService.State datastoreState = new DatastoreService.State();
      datastoreState.name = "image-datastore-name";
      datastoreState.id = imageDatastoreId;
      datastoreState.documentSelfLink = imageDatastoreId;
      datastoreState.type = "type";
      datastoreState.isImageDatastore = true;

      xenonClient.post(DatastoreServiceFactory.SELF_LINK, datastoreState);

      imageBackend.updateImageDatastore(imageEntity.getId(), imageDatastoreId);

      final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("imageId", imageEntity.getId());
      List<ImageToImageDatastoreMappingService.State> results =
          xenonClient.queryDocuments(ImageToImageDatastoreMappingService.State.class,
              termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));

      final ImmutableMap.Builder<String, String> terms = new ImmutableMap.Builder<>();
      terms.put("documentSelfLink", ImageServiceFactory.SELF_LINK + "/" + imageEntity.getId());
      List<ImageService.State> images =
          xenonClient.queryDocuments(ImageService.State.class, terms.build());
      assertThat(images.size(), is(1));
      assertThat(images.get(0).replicatedDatastore, is(1));
      assertThat(images.get(0).replicatedImageDatastore, is(1));
      assertThat(images.get(0).totalDatastore, is(1));
      assertThat(images.get(0).totalImageDatastore, is(1));
    }

    @Test
    public void testUpdateImageDatastoreTwice() throws Throwable {
      imageName = UUID.randomUUID().toString();
      String imageDatastoreId = "image-datastore-id-2";
      String imageId = createImageDocument(xenonClient, imageName, ImageState.READY, 1L, 10, 8, 5, 2);
      ImageEntity imageEntity = imageBackend.findById(imageId);

      DatastoreService.State datastoreState = new DatastoreService.State();
      datastoreState.name = "image-datastore-name";
      datastoreState.id = imageDatastoreId;
      datastoreState.documentSelfLink = imageDatastoreId;
      datastoreState.type = "type";
      datastoreState.isImageDatastore = true;
      xenonClient.post(DatastoreServiceFactory.SELF_LINK, datastoreState);

      imageBackend.updateImageDatastore(imageEntity.getId(), imageDatastoreId);

      final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("imageId", imageEntity.getId());
      List<ImageToImageDatastoreMappingService.State> results =
          xenonClient.queryDocuments(ImageToImageDatastoreMappingService.State.class,
              termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));

      imageBackend.updateImageDatastore(imageEntity.getId(), imageDatastoreId);
      results = xenonClient.queryDocuments(ImageToImageDatastoreMappingService.State.class,
          termsBuilder.build());
      assertThat(results.size(), is(1));
      assertThat(results.get(0).imageDatastoreId, is(imageDatastoreId));
    }
  }

  /**
   * Tests for checking the progress of image seeding.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ImageSeedingProgressCheckTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ImageBackend imageBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
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
    public void testImageSeedingInProgress() throws Throwable {
      String imageId = createImageDocument(xenonClient, "image-name", ImageState.READY, 1L, 10, 3, 3, 2);

      // Create Image to DataStore mapping services
      List<String> imageDatastores = Arrays.asList(new String[]{"datastore1", "datastore2"});
      for (String datastoreId : imageDatastores) {
        createImageToImageDatastoreMappingService(imageId, datastoreId);
      }

      // An unrelated imagestore, and it should not be returned by getSeededImageDatastores
      createImageToImageDatastoreMappingService("image2", "datastore4");

      // Assert
      boolean done = imageBackend.isImageSeedingDone(imageId);
      assertThat(done, is(false));

      List<String> candidateDatastores = imageBackend.getSeededImageDatastores(imageId);
      assertThat(CollectionUtils.isEqualCollection(imageDatastores, candidateDatastores), is(true));
    }

    @Test
    public void testImageSeedingFinished() throws Throwable {
      String imageId = createImageDocument(xenonClient, "image-name", ImageState.READY, 1L, 10, 3, 3, 3);

      // Create Image to DataStore mapping services
      List<String> imageDatastores = Arrays.asList(new String[]{"datastore1", "datastore2", "datastore3"});
      for (String datastoreId : imageDatastores) {
        createImageToImageDatastoreMappingService(imageId, datastoreId);
      }

      // An unrelated imagestore, and it should not be returned by getSeededImageDatastores
      createImageToImageDatastoreMappingService("image2", "datastore3");

      boolean done = imageBackend.isImageSeedingDone(imageId);
      assertThat(done, is(true));

      List<String> candidateDatastores = imageBackend.getSeededImageDatastores(imageId);
      assertThat(CollectionUtils.isEqualCollection(imageDatastores, candidateDatastores), is(true));
    }
  }
}
