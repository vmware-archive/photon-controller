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

import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageSetting;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException.Type;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perform image related operations using dcp based cloud store.
 */
@Singleton
public class ImageDcpBackend implements ImageBackend {

  private static final Logger logger = LoggerFactory.getLogger(ImageDcpBackend.class);

  private final ApiFeXenonRestClient dcpClient;

  private final VmBackend vmBackend;

  private final TaskBackend taskBackend;

  private final EntityLockBackend entityLockBackend;

  private final TombstoneBackend tombstoneBackend;

  @Inject
  public ImageDcpBackend(ApiFeXenonRestClient dcpClient, VmBackend vmBackend,
                         TaskBackend taskBackend,
                         EntityLockBackend entityLockBackend,
                         TombstoneBackend tombstoneBackend) {
    this.dcpClient = dcpClient;
    this.vmBackend = vmBackend;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.dcpClient.start();
  }

  @Override
  public ImageEntity deriveImage(ImageCreateSpec imageCreateSpec, ImageEntity originalImage) {
    ImageService.State imageServiceState = new ImageService.State();
    imageServiceState.name = imageCreateSpec.getName();
    imageServiceState.state = ImageState.CREATING;
    imageServiceState.replicationType = imageCreateSpec.getReplicationType();
    imageServiceState.size = originalImage.getSize();

    List<ImageService.State.ImageSetting> imageSettingsList = new ArrayList<>();
    for (ImageSettingsEntity imageSettingsEntity : originalImage.getImageSettings()) {
      ImageService.State.ImageSetting imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = imageSettingsEntity.getName();
      imageSetting.defaultValue = imageSettingsEntity.getDefaultValue();
      if (StringUtils.isBlank(imageSetting.defaultValue)) {
        imageSetting.defaultValue = "";
      }
      imageSettingsList.add(imageSetting);
    }
    imageServiceState.imageSettings = imageSettingsList;

    com.vmware.xenon.common.Operation result = dcpClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);

    ImageService.State createdState = result.getBody(ImageService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    ImageEntity imageEntity = convertToEntity(createdState);
    imageEntity.setId(id);

    return imageEntity;
  }

  @Override
  public TaskEntity prepareImageUpload(InputStream inputStream, String imageFileName,
                                       ImageReplicationType replicationType) throws ExternalException {
    if (StringUtils.isBlank(imageFileName)) {
      throw new ImageUploadException("Image file name cannot be blank.");
    }

    String name = Paths.get(imageFileName).getFileName().toString();
    logger.info("Prepare create image task, image name: {}", name);

    ImageService.State imageServiceState = new ImageService.State();
    imageServiceState.name = name;
    imageServiceState.state = ImageState.CREATING;
    imageServiceState.replicationType = replicationType;

    com.vmware.xenon.common.Operation result = dcpClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);
    ImageService.State createdState = result.getBody(ImageService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    ImageEntity imageEntity = convertToEntity(createdState);
    imageEntity.setId(id);

    TaskEntity task = uploadTask(inputStream, imageEntity);
    return task;
  }

  @Override
  public TaskEntity prepareImageDelete(String id) throws ExternalException {
    logger.info("Prepare delete image task, image id: {}", id);
    ImageEntity image = findById(id);
    if (ImageState.PENDING_DELETE.equals(image.getState())) {
      throw new InvalidImageStateException(
          String.format("Invalid operation to delete image %s in state PENDING_DELETE", image.getId()));
    }

    TaskEntity task = deleteTask(image);
    return task;
  }

  @Override
  public void tombstone(ImageEntity image) throws ExternalException {
    List<Vm> vmsInUse = vmBackend.filterByImage(image.getId());
    if (!vmsInUse.isEmpty()) {
      logger.info("vm(s) {} are using image {}, mark image as PENDING_DELETE", vmsInUse, image);
      updateState(image, ImageState.PENDING_DELETE);
      return;
    }

    tombstoneBackend.create(ImageEntity.KIND, image.getId());

    dcpClient.delete(
        ImageServiceFactory.SELF_LINK + "/" + image.getId(),
        new ImageService.State());
  }

  @Override
  public void updateState(ImageEntity imageEntity, ImageState state) throws ExternalException {
    ImageService.State imageState = new ImageService.State();
    imageState.state = state;
    patchImageService(imageEntity.getId(), imageState);
  }

  @Override
  public void updateSize(ImageEntity imageEntity, Long size) throws ExternalException {
    ImageService.State imageState = new ImageService.State();
    imageState.size = size;
    patchImageService(imageEntity.getId(), imageState);
  }

  @Override
  public ResourceList<ImageEntity> getAll(Optional<Integer> pageSize) throws ExternalException {
    return findEntitiesByName(Optional.<String>absent(), pageSize);
  }

  public ResourceList<Image> getImagesPage(String pageLink) throws PageExpiredException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = dcpClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
        ImageService.State.class, queryResult, state -> toApiRepresentation(state));
  }

  public Image toApiRepresentation(ImageService.State imageDocument) {
    ImageEntity imageEntity = convertToEntity(imageDocument);
    return toApiRepresentation(imageEntity);
  }

  @Override
  public Image toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public void updateSettings(ImageEntity imageEntity, Map<String, String> imageSettings)
      throws ExternalException {
    List<ImageService.State.ImageSetting> imageSettingsList = new ArrayList<>();

    Set<String> properties = imageSettings.keySet();
    for (String propertyName : properties) {
      ImageService.State.ImageSetting imageSetting = new ImageService.State.ImageSetting();
      imageSetting.name = propertyName;

      String propertyValue = imageSettings.get(propertyName);
      imageSetting.defaultValue = StringUtils.isBlank(propertyValue) ? "" : propertyValue;

      imageSettingsList.add(imageSetting);
    }

    ImageService.State imageState = new ImageService.State();
    imageState.imageSettings = imageSettingsList;
    patchImageService(imageEntity.getId(), imageState);
  }

  @Override
  public ImageEntity findById(String id) throws ExternalException {
    com.vmware.xenon.common.Operation result;
    try {
      result = dcpClient.get(ImageServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new ImageNotFoundException(Type.ID, id);
    }

    return convertToEntity(result.getBody(ImageService.State.class));
  }

  @Override
  public void updateImageDatastore(String imageId, String imageDatastoreName) throws ExternalException {
    checkNotNull(imageId, "imageId can not be null.");
    checkNotNull(imageDatastoreName, "imageDatastoreName can not be null");
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("name", imageDatastoreName);
    termsBuilder.put("isImageDatastore", "true");
    List<DatastoreService.State> datastores = dcpClient.queryDocuments(DatastoreService.State.class,
        termsBuilder.build());

    if (datastores.size() != 1) {

      //
      // Get all of the datastore documents from cloud store and dump them to the log.
      //

      try {
        com.vmware.xenon.common.Operation result = dcpClient.get(DatastoreServiceFactory.SELF_LINK);
        QueryTask queryTask = result.getBody(QueryTask.class);
        logger.info("Found datastores: " + Utils.toJson(true, false, queryTask));
      } catch (DocumentNotFoundException | XenonRuntimeException e) {
        // Ignore failures -- this is just for logging purposes
      }

      logger.error("expected exactly 1 imageDatastore found {} [{}]", datastores.size(),
          Utils.toJson(false, false, datastores));
      throw new ExternalException("expected exactly 1 imageDatastore found [" + datastores.size() + "]");
    }

    try {
      ImageToImageDatastoreMappingService.State state =
          new ImageToImageDatastoreMappingService.State();
      state.imageDatastoreId = datastores.get(0).id;
      state.imageId = imageId;
      state.documentSelfLink = imageId + "_" + datastores.get(0).id;

      dcpClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, state);
      logger.info("ImageToImageDatastoreMappingServiceState created with imageId {}, ImageDatastore {}", imageId,
          datastores.get(0).id);
    } catch (XenonRuntimeException e) {
      if (e.getCompletedOperation().getStatusCode() ==
          com.vmware.xenon.common.Operation.STATUS_CODE_CONFLICT) {
        return;
      }
      throw e;
    }
    updateImageCounts(imageId);
  }

  @Override
  public boolean isImageSeedingDone(String imageId) throws ExternalException {
    try {
      ImageService.State state = dcpClient.get(ImageServiceFactory.SELF_LINK + "/" + imageId)
          .getBody(ImageService.State.class);
      return state.totalImageDatastore.equals(state.replicatedImageDatastore);
    } catch (DocumentNotFoundException e) {
      throw new ImageNotFoundException(ImageNotFoundException.Type.ID, imageId);
    }
  }

  @Override
  public List<String> getSeededImageDatastores(String imageId) throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("imageId", imageId);

    ServiceDocumentQueryResult queryResult = dcpClient.queryDocuments(
        ImageToImageDatastoreMappingService.State.class, termsBuilder.build(), Optional.<Integer>absent(), true, false);
    List<String> seededImageDatastores = new ArrayList<>();
    queryResult.documents.values().forEach(item -> {
      seededImageDatastores.add(Utils.fromJson(item, ImageToImageDatastoreMappingService.State.class).imageDatastoreId);
    });

    return seededImageDatastores;
  }

  @Override
  public ResourceList<Image> filter(Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    ServiceDocumentQueryResult queryResult = findDocumentsByName(name, pageSize);
    return PaginationUtils.xenonQueryResultToResourceList(ImageService.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  private void patchImageService(String imageId, ImageService.State imageState)
      throws ImageNotFoundException {
    try {
      dcpClient.patch(
          ImageServiceFactory.SELF_LINK + "/" + imageId, imageState);
    } catch (DocumentNotFoundException e) {
      throw new ImageNotFoundException(Type.ID, imageId);
    }
  }

  private ImageEntity convertToEntity(ImageService.State imageState) {
    ImageEntity imageEntity = new ImageEntity();
    imageEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(imageState.documentSelfLink));
    imageEntity.setName(imageState.name);
    imageEntity.setReplicationType(imageState.replicationType);
    imageEntity.setState(imageState.state);
    imageEntity.setSize(imageState.size);
    imageEntity.setTotalDatastore(imageState.totalDatastore);
    imageEntity.setTotalImageDatastore(imageState.totalImageDatastore);
    imageEntity.setReplicatedDatastore(imageState.replicatedDatastore);
    imageEntity.setReplicatedImageDatastore(imageState.replicatedImageDatastore);

    List<ImageSettingsEntity> imageSettingsEntityList = new ArrayList<>();

    if (imageState.imageSettings != null) {
      for (ImageService.State.ImageSetting imageSetting : imageState.imageSettings) {
        ImageSettingsEntity imageSettingsEntity = new ImageSettingsEntity();
        imageSettingsEntity.setName(imageSetting.name);
        imageSettingsEntity.setDefaultValue(imageSetting.defaultValue);
        imageSettingsEntity.setImage(imageEntity);
        imageSettingsEntity.setId(imageEntity.getId());
        imageSettingsEntityList.add(imageSettingsEntity);
      }
    }

    imageEntity.setImageSettings(imageSettingsEntityList);

    return imageEntity;
  }

  private TaskEntity uploadTask(InputStream inputStream, ImageEntity image) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(image);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.createOrUpdateTransientResource(IsoUploadStepCmd.INPUT_STREAM, inputStream);
    step.addResources(entityList);
    step.setOperation(Operation.UPLOAD_IMAGE);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.REPLICATE_IMAGE);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.QUERY_REPLICATE_IMAGE_TASK_RESULT);

    TaskEntity task = taskBackend.createTaskWithSteps(image, Operation.CREATE_IMAGE, false, stepEntities);
    task.getToBeLockedEntities().add(image);

    return task;
  }

  private ServiceDocumentQueryResult findDocumentsByName(Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    return dcpClient.queryDocuments(ImageService.State.class, termsBuilder.build(), pageSize, true);
  }

  private ResourceList<ImageEntity> findEntitiesByName(Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {

    ServiceDocumentQueryResult queryResult = findDocumentsByName(name, pageSize);
    return PaginationUtils.xenonQueryResultToResourceList(ImageService.State.class, queryResult,
        state -> convertToEntity(state));
  }


  private TaskEntity deleteTask(ImageEntity image) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(image);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DELETE_IMAGE);

    TaskEntity task = taskBackend.createTaskWithSteps(image, Operation.DELETE_IMAGE, false, stepEntities);
    task.getToBeLockedEntities().add(image);

    return task;
  }

  private Image toApiRepresentation(ImageEntity imageEntity) {
    Image image = new Image();

    image.setId(imageEntity.getId());
    image.setName(imageEntity.getName());
    image.setState(imageEntity.getState());
    image.setSize(imageEntity.getSize());
    image.setReplicationType(imageEntity.getReplicationType());
    image.setSettings(createImageSettingListApiRepresentation(imageEntity.getImageSettings()));
    if (imageEntity.getTotalDatastore() != null &&
        imageEntity.getReplicatedDatastore() != null &&
        imageEntity.getTotalDatastore() != 0) {
      String replicatedDatastoreRatio =
          Math.round(imageEntity.getReplicatedDatastore() * 100.00 / imageEntity.getTotalDatastore()) + "%";
      image.setReplicationProgress(replicatedDatastoreRatio);
    }

    if (imageEntity.getTotalImageDatastore() != null &&
        imageEntity.getReplicatedImageDatastore() != null &&
        imageEntity.getTotalImageDatastore() != 0) {
      String replicatedImageDatastoreRatio =
          Math.round(imageEntity.getReplicatedImageDatastore() * 100.00 / imageEntity.getTotalImageDatastore()) + "%";
      image.setSeedingProgress(replicatedImageDatastoreRatio);
    }

    return image;
  }

  private ImageSetting createImageSettingApiRepresentation(ImageSettingsEntity imageSettingsEntity) {
    ImageSetting imageSetting = new ImageSetting();

    imageSetting.setName(imageSettingsEntity.getName());
    imageSetting.setDefaultValue(imageSettingsEntity.getDefaultValue());

    return imageSetting;
  }

  private List<ImageSetting> createImageSettingListApiRepresentation(List<ImageSettingsEntity> imageSettingsEntities) {
    List<ImageSetting> imageSettings = new ArrayList<>();

    for (ImageSettingsEntity imageSettingsEntity : imageSettingsEntities) {
      imageSettings.add(createImageSettingApiRepresentation(imageSettingsEntity));
    }

    return imageSettings;
  }

  /**
   * Gets image entity and sends patch to update total datastore and total image datastore field.
   */
  protected void updateImageCounts(String imageId) throws ExternalException {
    try {
      com.vmware.xenon.common.Operation result = dcpClient.postToBroadcastQueryService(buildDatastoreSetQuery());

      // build the image entity update patch
      ImageService.State patchState = new ImageService.State();
      patchState.replicatedImageDatastore = 1;
      patchState.replicatedDatastore = 1;

      patchState.totalImageDatastore = 0;
      patchState.totalDatastore = 0;
      List<DatastoreService.State> documentLinks = QueryTaskUtils
          .getBroadcastQueryDocuments(DatastoreService.State.class, result);
      patchState.totalDatastore = documentLinks.size();
      for (DatastoreService.State state : documentLinks) {
        if (state.isImageDatastore) {
          patchState.totalImageDatastore++;
        }
      }
      dcpClient.patch(ImageServiceFactory.SELF_LINK + "/" + imageId, patchState);
    } catch (DocumentNotFoundException e) {
      throw new ImageNotFoundException(Type.ID, imageId);
    }
  }

  /**
   * Build a QuerySpecification for querying image data store.
   *
   * @return
   */
  private QueryTask.QuerySpecification buildDatastoreSetQuery() {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return querySpecification;
  }
}
