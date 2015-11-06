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
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.commands.steps.ImageUploadStepCmd;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.ImageSettingsDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException.Type;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Perform image related operations.
 */
@Singleton
public class ImageSqlBackend implements ImageBackend {

  private static final Logger logger = LoggerFactory.getLogger(ImageSqlBackend.class);

  private final ImageDao imageDao;

  private final VmDao vmDao;

  private final ImageSettingsDao imageSettingsDao;

  private final TaskBackend taskBackend;

  private final EntityLockBackend entityLockBackend;

  private final TombstoneBackend tombstoneBackend;

  @Inject
  public ImageSqlBackend(ImageDao imageDao, VmDao vmDao, ImageSettingsDao imageSettingsDao, TaskBackend taskBackend,
                         EntityLockBackend entityLockBackend, TombstoneBackend tombstoneBackend) {
    this.imageDao = imageDao;
    this.vmDao = vmDao;
    this.imageSettingsDao = imageSettingsDao;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
  }

  @Transactional
  public ImageEntity deriveImage(ImageCreateSpec imageCreateSpec, ImageEntity originalImage) {
    ImageEntity image = new ImageEntity();
    image.setName(imageCreateSpec.getName());
    image.setReplicationType(imageCreateSpec.getReplicationType());
    image.setState(ImageState.CREATING);
    image.setSize(originalImage.getSize());
    imageDao.create(image);

    List<ImageSettingsEntity> imageSettings = new ArrayList<>();
    for (ImageSettingsEntity imageSetting : originalImage.getImageSettings()) {
      ImageSettingsEntity imageSettingsEntity = new ImageSettingsEntity();
      imageSettingsEntity.setName(imageSetting.getName());
      imageSettingsEntity.setDefaultValue(imageSetting.getDefaultValue());
      imageSettingsEntity.setImage(image);
      imageSettingsDao.create(imageSettingsEntity);
      imageSettings.add(imageSettingsEntity);
    }
    image.setImageSettings(imageSettings);
    imageDao.update(image);

    return image;
  }

  @Transactional
  public TaskEntity prepareImageUpload(InputStream inputStream, String imageFileName,
                                       ImageReplicationType replicationType) throws ExternalException {
    if (StringUtils.isBlank(imageFileName)) {
      throw new ImageUploadException("Image file name cannot be blank.");
    }

    String name = Paths.get(imageFileName).getFileName().toString();
    logger.info("Prepare create image task, image name: {}", name);
    ImageEntity image = createImageEntity(name, replicationType);
    TaskEntity task = uploadTask(inputStream, image);
    logger.info("Task created: {}", task);
    return task;
  }

  @Transactional
  public TaskEntity prepareImageDelete(String id)
      throws ExternalException {
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

  @Transactional
  public void tombstone(ImageEntity image) throws ExternalException {
    List<VmEntity> vmsInUse = vmDao.listByImage(image.getId());
    if (!vmsInUse.isEmpty()) {
      logger.info("vm(s) {} are using image {}, mark image as PENDING_DELETE", vmsInUse, image);
      updateState(image, ImageState.PENDING_DELETE);
      return;
    }

    tombstoneBackend.create(ImageEntity.KIND, image.getId());
    imageDao.delete(image);
  }

  @Transactional
  public void updateState(ImageEntity imageEntity, ImageState state) throws ExternalException {
    imageEntity.setState(state);
    imageDao.update(imageEntity);
  }

  @Transactional
  public void updateSize(ImageEntity imageEntity, Long size) throws ExternalException {
    imageEntity.setSize(size);
    imageDao.update(imageEntity);
  }

  @Transactional
  public List<ImageEntity> getAll() throws ExternalException {
    return imageDao.listAll();
  }

  @Transactional
  public Image toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Transactional
  public List<Image> getListApiRepresentation() throws ExternalException {
    List<Image> resourceList = new ArrayList<>();
    List<ImageEntity> list = getAll();

    for (ImageEntity entity : list) {
      resourceList.add(toApiRepresentation(entity));
    }

    return resourceList;
  }

  @Transactional
  public void updateSettings(ImageEntity imageEntity, Map<String, String> imageSettings)
      throws ExternalException {
    List<ImageSettingsEntity> imageSettingsEntityList = new ArrayList<>();

    Set<String> properties = imageSettings.keySet();
    for (String propertyName : properties) {
      ImageSettingsEntity imageSettingsEntity = new ImageSettingsEntity();
      imageSettingsEntity.setName(propertyName);

      String propertyValue = imageSettings.get(propertyName);
      imageSettingsEntity.setDefaultValue(StringUtils.isBlank(propertyValue) ? "" : propertyValue);

      imageSettingsEntityList.add(imageSettingsEntity);
    }

    imageEntity.setImageSettings(imageSettingsEntityList);

    for (ImageSettingsEntity imageSettingsEntity : imageSettingsEntityList) {
      imageSettingsEntity.setImage(imageEntity);
      imageSettingsDao.create(imageSettingsEntity);
    }

    imageDao.update(imageEntity);
  }

  @Transactional
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    ImageEntity imageEntity = findById(id);
    return taskBackend.filter(imageEntity.getId(), imageEntity.getKind(), state);
  }

  // findById method should be nested inside other @Transactional method
  public ImageEntity findById(String id) throws ExternalException {
    ImageEntity image = getById(id);

    if (image == null) {
      throw new ImageNotFoundException(Type.ID, id);
    }

    return image;
  }

  private Image toApiRepresentation(ImageEntity imageEntity) {
    Image image = new Image();

    image.setId(imageEntity.getId());
    image.setName(imageEntity.getName());
    image.setState(imageEntity.getState());
    image.setSize(imageEntity.getSize());
    image.setReplicationType(imageEntity.getReplicationType());
    image.setSettings(createImageSettingListApiRepresentation(imageEntity.getImageSettings()));

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

  private ImageEntity getById(String id) {
    return imageDao.findById(id).orNull();
  }

  private TaskEntity uploadTask(InputStream inputStream, ImageEntity image) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(image, Operation.CREATE_IMAGE);

    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, image, Operation.UPLOAD_IMAGE);
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, inputStream);
    entityLockBackend.setStepLock(image, step);

    BackendHelpers.createReplicateImageStep(taskBackend, image, task);
    return task;
  }

  private TaskEntity deleteTask(ImageEntity image) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(image, Operation.DELETE_IMAGE);
    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, image, Operation.DELETE_IMAGE);
    entityLockBackend.setStepLock(image, step);

    taskBackend.getStepBackend().createQueuedStep(task, image, Operation.DELETE_IMAGE_REPLICAS);
    return task;
  }

  private ImageEntity createImageEntity(String imageName, ImageReplicationType replicationType) {
    ImageEntity image = new ImageEntity();
    image.setName(imageName);
    image.setState(ImageState.CREATING);
    image.setReplicationType(replicationType);
    imageDao.create(image);

    return image;
  }

}
