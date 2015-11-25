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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException.Type;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perform image related operations using dcp based cloud store.
 */
@Singleton
public class ImageDcpBackend implements ImageBackend {

  private static final Logger logger = LoggerFactory.getLogger(ImageDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;

  private final VmBackend vmBackend;

  private final TaskBackend taskBackend;

  private final EntityLockBackend entityLockBackend;

  private final TombstoneBackend tombstoneBackend;

  @Inject
  public ImageDcpBackend(ApiFeDcpRestClient dcpClient, VmBackend vmBackend,
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
    logger.info("Task created: {}", task);
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
    logger.info("Task created: {}", task);
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
  public List<ImageEntity> getAll() throws ExternalException {
    return findEntitiesByName(Optional.<String>absent());
  }

  @Override
  public Image toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public List<Image> getListApiRepresentation() throws ExternalException {
    List<Image> resourceList = new ArrayList<>();
    List<ImageEntity> list = getAll();

    for (ImageEntity entity : list) {
      resourceList.add(toApiRepresentation(entity));
    }

    return resourceList;
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
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    ImageEntity imageEntity = findById(id);
    return taskBackend.filter(imageEntity.getId(), imageEntity.getKind(), state);
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
    checkState(datastores.size() == 1, "more than one image datastore has the same name");

    try {
      ImageReplicationService.State imageReplicationServiceState = new ImageReplicationService.State();
      imageReplicationServiceState.imageDatastoreId = datastores.get(0).id;
      imageReplicationServiceState.imageId = imageId;
      imageReplicationServiceState.documentSelfLink = imageId + "-" + datastores.get(0).id;

      dcpClient.post(ImageReplicationServiceFactory.SELF_LINK, imageReplicationServiceState);
      logger.info("ImageReplicationServiceState created with imageId {}, ImageDatastore {}", imageId,
          datastores.get(0).id);
    } catch (DcpRuntimeException e) {
      if (e.getCompletedOperation().getStatusCode() ==
          com.vmware.xenon.common.Operation.STATUS_CODE_CONFLICT) {
        return;
      }
      throw e;
    }
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

    switch (image.getReplicationType()) {
      case EAGER:
        step = new StepEntity();
        stepEntities.add(step);
        step.addResources(entityList);
        step.setOperation(Operation.REPLICATE_IMAGE);
        break;
      case ON_DEMAND:
        break;
      default:
        throw new ExternalException("Image Replication Type not supported " + image.getReplicationType());
    }

    TaskEntity task = taskBackend.createTaskWithSteps(image, Operation.CREATE_IMAGE, false, stepEntities);

    task.getLockableEntityIds().add(image.getId());

    return task;
  }

  private List<ImageService.State> findDocumentsByName(Optional<String> name) throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    return dcpClient.queryDocuments(ImageService.State.class, termsBuilder.build());
  }

  private List<ImageEntity> findEntitiesByName(Optional<String> name) throws ExternalException {
    List<ImageEntity> imageEntityList = null;
    List<ImageService.State> imageStateList = findDocumentsByName(name);
    if (imageStateList != null) {
      imageEntityList = new ArrayList<>(imageStateList.size());
      for (ImageService.State imageState : imageStateList) {
        imageEntityList.add(convertToEntity(imageState));
      }
    }

    return imageEntityList;
  }

  private TaskEntity deleteTask(ImageEntity image) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(image);


    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DELETE_IMAGE);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DELETE_IMAGE_REPLICAS);

    TaskEntity task = taskBackend.createTaskWithSteps(image, Operation.DELETE_IMAGE, false, stepEntities);
    task.getLockableEntityIds().add(image.getId());

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
          (imageEntity.getReplicatedDatastore() * 100.00 / imageEntity.getTotalDatastore()) + "%";
      image.setReplicationProgress(replicatedDatastoreRatio);
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
}
