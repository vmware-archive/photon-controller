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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.commands.steps.ImageUploadStepCmd;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.ImageSettingsDao;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.FileAssert.fail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Test {@link ImageBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class ImageSqlBackendTest extends BaseDaoTest {

  @Inject
  private ImageBackend imageBackend;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private ImageDao imageDao;

  @Inject
  private ImageSettingsDao imageSettingsDao;

  @Inject
  private EntityFactory entityFactory;

  @Mock
  private InputStream inputStream;

  private String imageName = "image-name";

  @Test
  public void testDeriveImage() throws Throwable {
    ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
    imageCreateSpec.setName("i1");
    imageCreateSpec.setReplicationType(ImageReplicationType.EAGER);

    ImageEntity vmImage = entityFactory.createImage("image1", ImageState.READY,
        ImageReplicationType.EAGER, 100L, "n1", "v1", "n2", "v2");

    flushSession();

    String imageId = imageBackend.deriveImage(imageCreateSpec, vmImage).getId();

    flushSession();

    ImageEntity image = imageBackend.findById(imageId);
    assertThat(image.getName(), is(imageCreateSpec.getName()));
    assertThat(image.getReplicationType(), is(imageCreateSpec.getReplicationType()));
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getSize(), is(100L));
    assertThat(image.getImageSettingsMap(), is((Map<String, String>) ImmutableMap.of("n1", "v1", "n2", "v2")));
  }

  @Test
  public void testPrepareImageUploadEager() throws ExternalException {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.EAGER);
    assertThat(task.getSteps().size(), is(2));

    StepEntity step = task.getSteps().get(0);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.UPLOAD_IMAGE));
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);
    assertThat(image.getName(), is("image-name"));
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getReplicationType(), is(ImageReplicationType.EAGER));
    assertThat((InputStream) step.getTransientResource(ImageUploadStepCmd.INPUT_STREAM), is(inputStream));

    step = task.getSteps().get(1);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.REPLICATE_IMAGE));
    assertThat((ImageEntity) step.getTransientResourceEntities().get(0), is(image));
    assertThat(step.getTransientResource(ImageUploadStepCmd.INPUT_STREAM), nullValue());
  }

  @Test
  public void testPrepareImageUploadOnDemand() throws ExternalException {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.ON_DEMAND);
    assertThat(task.getSteps().size(), is(1));

    StepEntity step = task.getSteps().get(0);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.UPLOAD_IMAGE));
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);
    assertThat(image.getName(), is("image-name"));
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getReplicationType(), is(ImageReplicationType.ON_DEMAND));
    assertThat((InputStream) step.getTransientResource(ImageUploadStepCmd.INPUT_STREAM), is(inputStream));
  }

  @Test(dataProvider = "ImageFileNames")
  public void testPrepareImageUploadImageFileNames(String imageFileName) throws ExternalException {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, imageFileName, ImageReplicationType.ON_DEMAND);
    assertThat(task.getSteps().size(), is(1));

    StepEntity step = task.getSteps().get(0);
    assertThat(step.getTransientResourceEntities().size(), is(1));
    assertThat(step.getOperation(), is(Operation.UPLOAD_IMAGE));
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);
    assertThat(image.getState(), is(ImageState.CREATING));
    assertThat(image.getName(), is(imageName));
  }

  @DataProvider(name = "ImageFileNames")
  public Object[][] getImageNames() {
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
  public void testPrepareImageDelete() throws Throwable {
    ImageEntity imageInDb = new ImageEntity();
    imageInDb.setName("image-0");
    imageInDb.setState(ImageState.READY);
    imageInDb.setSize(1000L);

    imageDao.create(imageInDb);
    TaskEntity taskDelete = imageBackend.prepareImageDelete(imageInDb.getId());
    assertThat(taskDelete.getSteps().size(), is(2));
    assertThat(taskDelete.getSteps().get(0).getOperation(), is(Operation.DELETE_IMAGE));
    assertThat(taskDelete.getSteps().get(1).getOperation(), is(Operation.DELETE_IMAGE_REPLICAS));
  }

  @Test(expectedExceptions = InvalidImageStateException.class)
  public void testPrepareImageDeleteInPendingDelete() throws Throwable {
    ImageEntity imageInDb = new ImageEntity();
    imageInDb.setName("image-0");
    imageInDb.setState(ImageState.PENDING_DELETE);
    imageInDb.setSize(1000L);

    imageDao.create(imageInDb);
    imageBackend.prepareImageDelete(imageInDb.getId());
  }

  @Test
  public void testUploadingImageWithDuplicatedName() throws NameTakenException, ExternalException {
    assertThat(imageBackend.getAll().isEmpty(), is(true));
    imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.ON_DEMAND);
    imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.ON_DEMAND);
    flushSession();

    assertThat(imageBackend.getAll().size(), is(2));
  }

  @Test(expectedExceptions = ConcurrentTaskException.class)
  public void testDeletingAnUploadingImage() throws Throwable {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.ON_DEMAND);
    StepEntity step = task.getSteps().get(0);
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);

    imageBackend.prepareImageDelete(image.getId());
  }

  @Test(expectedExceptions = ImageNotFoundException.class)
  public void testDeletingAnNonExistingImage() throws Throwable {
    imageBackend.prepareImageDelete("non-existing-id");
  }

  @Test
  public void testTombstone() throws Throwable {
    TaskEntity task = imageBackend.prepareImageUpload(inputStream, "image-name", ImageReplicationType.ON_DEMAND);
    StepEntity step = task.getSteps().get(0);
    ImageEntity image = (ImageEntity) step.getTransientResourceEntities().get(0);
    assertThat(image.getState(), is(ImageState.CREATING));

    image.setState(ImageState.READY);
    flushSession();

    TenantEntity tenant = new TenantEntity();
    tenant.setName("t1");
    tenantDao.create(tenant);

    ProjectEntity project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    projectDao.create(project);

    VmEntity vm = new VmEntity();
    vm.setName("vm-1");
    vm.setProjectId(project.getId());
    vm.setImageId(image.getId());
    vmDao.create(vm);

    flushSession();

    imageBackend.tombstone(image);
    assertThat(image.getState(), is(ImageState.PENDING_DELETE));

    vm = vmDao.findById(vm.getId()).get();
    vmDao.delete(vm);
    flushSession();

    imageBackend.tombstone(image);
    try {
      imageBackend.findById(image.getId());
      fail("Image should be deleted in database");
    } catch (ImageNotFoundException e) {
    }
  }

  @Test
  public void testUpdateSettings() throws Throwable {
    ImageEntity imageEntity = new ImageEntity();
    imageEntity.setName("image-name");
    imageEntity.setSize(1000L);
    imageEntity.setState(ImageState.READY);

    imageDao.create(imageEntity);
    String imageId = imageEntity.getId();

    Map<String, String> imageSettings = new HashMap<>();
    imageSettings.put("property-1", "value-1");
    imageSettings.put("property-2", null);

    imageBackend.updateSettings(imageEntity, imageSettings);

    List<ImageSettingsEntity> imageSettingsEntityList = imageSettingsDao.listAll();
    assertThat(imageSettingsEntityList.size(), is(2));

    ImageSettingsEntity settings1 = imageSettingsEntityList.get(0);
    assertThat(settings1.getImage().getId(), is(imageId));
    assertThat(settings1.getName(), is("property-1"));
    assertThat(settings1.getDefaultValue(), is("value-1"));

    ImageSettingsEntity settings2 = imageSettingsEntityList.get(1);
    assertThat(settings2.getImage().getId(), is(imageId));
    assertThat(settings2.getName(), is("property-2"));
    assertThat(settings2.getDefaultValue(), is(""));

    assertThat(imageDao.listAll().size(), is(1));
    assertThat(imageDao.listAll().get(0).getImageSettings().size(), is(2));
  }

  @Test
  public void testGetTasks() throws Exception {
    ImageEntity imageInDb = new ImageEntity();
    imageInDb.setName("image-0");
    imageInDb.setState(ImageState.READY);
    imageInDb.setSize(1000L);

    imageDao.create(imageInDb);
    TaskEntity taskDelete = imageBackend.prepareImageDelete(imageInDb.getId());

    List<Task> tasks = imageBackend.getTasks(imageInDb.getId(), Optional.<String>absent());

    assertThat(tasks.size(), is(1));
    assertThat(tasks.get(0).getState(), is("QUEUED"));
  }

  @Test
  public void testGetTasksWithGivenState() throws Exception {
    ImageEntity imageInDb = new ImageEntity();
    imageInDb.setName("image-0");
    imageInDb.setState(ImageState.READY);
    imageInDb.setSize(1000L);

    imageDao.create(imageInDb);
    TaskEntity taskDelete = imageBackend.prepareImageDelete(imageInDb.getId());

    List<Task> tasks = imageBackend.getTasks(imageInDb.getId(), Optional.of("FINISHED"));

    assertThat(tasks.size(), is(0));
  }

  @Test(expectedExceptions = ImageNotFoundException.class,
      expectedExceptionsMessageRegExp = "^Image id 'image1' not found$")
  public void testGetTasksWithInvalidImageId() throws Exception {
    imageBackend.getTasks("image1", Optional.<String>absent());
  }
}
