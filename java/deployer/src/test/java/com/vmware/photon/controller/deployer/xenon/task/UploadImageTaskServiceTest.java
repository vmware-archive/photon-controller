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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.resource.ImagesApi;
import com.vmware.photon.controller.api.client.resource.TasksApi;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ApiTestUtils;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.ApiUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.apache.http.entity.mime.content.FileBody;
import org.mockito.Matchers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link UploadImageTaskService} class.
 */
public class UploadImageTaskServiceTest {

  /**
   * This dummy test case enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private UploadImageTaskService uploadImageTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      uploadImageTaskService = new UploadImageTaskService();
    }

    @Test
    public void testOptions() {
      assertThat(uploadImageTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link UploadImageTaskService#handleStart(Operation)}
   * method.
   */
  public class HandleStartTest {

    private TestHost testHost;
    private UploadImageTaskService uploadImageTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      uploadImageTaskService = new UploadImageTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully started.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(
        TaskState.TaskStage taskStage,
        UploadImageTaskService.TaskState.SubStage subStage) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(uploadImageTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadImageTaskService.State serviceState = testHost.getServiceState(UploadImageTaskService.State.class);
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.taskPollDelay, is(10));
      assertThat(serviceState.imageName, is("IMAGE_NAME"));
      assertThat(serviceState.imageFile, is("IMAGE_FILE"));

      if (taskStage == null) {
        assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.CREATED));
      } else {
        assertThat(serviceState.taskState.stage, is(taskStage));
      }

      assertThat(serviceState.taskState.subStage, is(subStage));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return TestHelper.getValidStartStages(UploadImageTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStartStages", expectedExceptions = BadRequestException.class)
    public void testInvalidStartState(
        TaskState.TaskStage taskStage,
        UploadImageTaskService.TaskState.SubStage subStage) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(taskStage, subStage);
      testHost.startServiceSynchronously(uploadImageTaskService, startState);
    }

    @DataProvider(name = "InvalidStartStages")
    public Object[][] getInvalidStartStages() {
      return TestHelper.getInvalidStartStages(UploadImageTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(uploadImageTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadImageTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link UploadImageTaskService#handlePatch(Operation)}
   * method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private UploadImageTaskService uploadImageTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      uploadImageTaskService = new UploadImageTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(
        TaskState.TaskStage startStage,
        UploadImageTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        UploadImageTaskService.TaskState.SubStage patchSubStage) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(uploadImageTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadImageTaskService.buildPatch(patchStage, patchSubStage));

      op = testHost.sendRequestAndWait(patchOp);
      assertThat(op.getStatusCode(), is(200));

      UploadImageTaskService.State serviceState = testHost.getServiceState(UploadImageTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(UploadImageTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        UploadImageTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        UploadImageTaskService.TaskState.SubStage patchSubStage) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(uploadImageTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadImageTaskService.buildPatch(patchStage, patchSubStage));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(UploadImageTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(uploadImageTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadImageTaskService.State patchState = UploadImageTaskService.buildPatch(TaskState.TaskStage.STARTED,
          UploadImageTaskService.TaskState.SubStage.UPLOAD_IMAGE);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadImageTaskService.State.class, Immutable.class));
    }

    @Test(dataProvider = "WriteOnceFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchWriteOnceFieldWrittenTwice(String fieldName) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(uploadImageTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadImageTaskService.State patchState = UploadImageTaskService.buildPatch(TaskState.TaskStage.STARTED,
          UploadImageTaskService.TaskState.SubStage.UPLOAD_IMAGE);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOp);
      } catch (BadRequestException e) {
        throw new RuntimeException(e);
      }

      UploadImageTaskService.State serviceState = testHost.getServiceState(UploadImageTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(ReflectionUtils.getDefaultAttributeValue(declaredField)));
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "WriteOnceFieldNames")
    public Object[][] getWriteOnceFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadImageTaskService.State.class, WriteOnce.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link UploadImageTaskService} task.
   */
  public class EndToEndTest {

    private final Task failedTask = ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage");

    private ApiClientFactory apiClientFactory;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private String imageId;
    private ImagesApi imagesApi;
    private ListeningExecutorService listeningExecutorService;
    private File sourceDirectory;
    private File sourceFile;
    private UploadImageTaskService.State startState;
    private TasksApi tasksApi;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          this.getClass().getResource("/config.yml").getPath()).getDeployerContext();
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .listeningExecutorService(listeningExecutorService)
          .hostCount(1)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      ApiClient apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      imagesApi = mock(ImagesApi.class);
      doReturn(imagesApi).when(apiClient).getImagesApi();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();

      imageId = UUID.randomUUID().toString();

      sourceDirectory = Files.createTempDirectory("source-ova-").toFile();
      sourceFile = TestHelper.createSourceFile("source.ova", sourceDirectory);

      doAnswer(
          (invocation) -> {
            FileBody fileBody = (FileBody) invocation.getArguments()[0];
            OutputStream outputStream = new FileOutputStream(new File(sourceDirectory, "output.ova"));
            fileBody.writeTo(outputStream);
            return TestHelper.createTask("UPLOAD_IMAGE_TASK_ID", imageId, "QUEUED");
          })
          .when(imagesApi)
          .uploadImage(any(FileBody.class), anyString());

      doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_IMAGE_TASK_ID", imageId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_IMAGE_TASK_ID", imageId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_IMAGE_TASK_ID", imageId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("UPLOAD_IMAGE_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetImageAsync(imageId, "33.33%"))
          .doAnswer(MockHelper.mockGetImageAsync(imageId, "66.67%"))
          .doAnswer(MockHelper.mockGetImageAsync(imageId, "100.0%"))
          .when(imagesApi)
          .getImageAsync(eq(imageId), Matchers.<FutureCallback<Image>>any());

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      DeploymentService.State deploymentState = TestHelper.createDeploymentService(cloudStoreEnvironment);

      for (int i = 0; i < 3; i++) {
        TestHelper.createVmService(testEnvironment);
      }

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.deploymentServiceLink = deploymentState.documentSelfLink;
      startState.imageFile = sourceFile.getAbsolutePath();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(sourceDirectory);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
      listeningExecutorService.shutdown();
    }

    @Test
    public void testSuccess() throws Throwable {

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.bytesUploaded, is(10485760L));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingProgress, is("100.0%"));
      assertThat(finalState.imageSeedingPollCount, is(3));

      verify(imagesApi).uploadImage(
          any(FileBody.class),
          eq(ImageReplicationType.ON_DEMAND.name()));

      verify(tasksApi, times(3)).getTaskAsync(
          eq("UPLOAD_IMAGE_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      verify(imagesApi, times(3)).getImageAsync(
          eq(imageId),
          Matchers.<FutureCallback<Image>>any());

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(VmService.State.class)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.BROADCAST,
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      QueryTask result = testEnvironment.sendQueryAndWait(queryTask);
      for (Object vmDocument : result.results.documents.values()) {
        VmService.State vmState = Utils.fromJson(vmDocument, VmService.State.class);
        assertThat(vmState.imageId, is(imageId));
      }

      DeploymentService.State deploymentState = cloudStoreEnvironment.getServiceState(finalState.deploymentServiceLink,
          DeploymentService.State.class);
      assertThat(deploymentState.imageId, is(imageId));
    }

    @Test
    public void testSuccessNoTaskPolling() throws Throwable {

      doAnswer(
          (invocation) -> {
            FileBody fileBody = (FileBody) invocation.getArguments()[0];
            OutputStream outputStream = new FileOutputStream(new File(sourceDirectory, "output.ova"));
            fileBody.writeTo(outputStream);
            return TestHelper.createTask("UPLOAD_IMAGE_TASK_ID", imageId, "COMPLETED");
          })
          .when(imagesApi)
          .uploadImage(any(FileBody.class), anyString());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.bytesUploaded, is(10485760L));
      assertThat(finalState.uploadImageTaskId, nullValue());
      assertThat(finalState.uploadImagePollCount, is(0));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingProgress, is("100.0%"));
      assertThat(finalState.imageSeedingPollCount, is(3));

      verify(imagesApi).uploadImage(
          any(FileBody.class),
          eq(ImageReplicationType.ON_DEMAND.name()));

      verify(imagesApi, times(3)).getImageAsync(
          eq(imageId),
          Matchers.<FutureCallback<Image>>any());

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(VmService.State.class)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.BROADCAST,
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      QueryTask result = testEnvironment.sendQueryAndWait(queryTask);
      for (Object vmDocument : result.results.documents.values()) {
        VmService.State vmState = Utils.fromJson(vmDocument, VmService.State.class);
        assertThat(vmState.imageId, is(imageId));
      }

      DeploymentService.State deploymentState = cloudStoreEnvironment.getServiceState(finalState.deploymentServiceLink,
          DeploymentService.State.class);
      assertThat(deploymentState.imageId, is(imageId));
    }

    @Test
    public void testSuccessNoImagePolling() throws Throwable {

      doAnswer(MockHelper.mockGetImageAsync(imageId, "100.0%"))
          .when(imagesApi)
          .getImageAsync(eq(imageId), Matchers.<FutureCallback<Image>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.bytesUploaded, is(10485760L));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingProgress, is("100.0%"));
      assertThat(finalState.imageSeedingPollCount, is(1));

      verify(imagesApi).uploadImage(
          any(FileBody.class),
          eq(ImageReplicationType.ON_DEMAND.name()));

      verify(tasksApi, times(3)).getTaskAsync(
          eq("UPLOAD_IMAGE_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      verify(imagesApi).getImageAsync(
          eq(imageId),
          Matchers.<FutureCallback<Image>>any());
    }

    @Test
    public void testUploadImageFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_IMAGE_TASK_ID", imageId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_IMAGE_TASK_ID", imageId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("UPLOAD_IMAGE_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, nullValue());

      verify(imagesApi).uploadImage(
          any(FileBody.class),
          eq(ImageReplicationType.ON_DEMAND.name()));

      verify(tasksApi, times(3)).getTaskAsync(
          eq("UPLOAD_IMAGE_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());
    }

    @Test
    public void testUploadImageFailureNoTaskPolling() throws Throwable {

      doReturn(failedTask).when(imagesApi).uploadImage(any(FileBody.class), anyString());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.uploadImageTaskId, nullValue());
      assertThat(finalState.uploadImagePollCount, is(0));
    }

    @Test
    public void testUploadImageFailureExceptionInUploadImageCall() throws Throwable {

      doThrow(new IOException("I/O exception in uploadImage call"))
          .when(imagesApi)
          .uploadImage(any(FileBody.class), anyString());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in uploadImage call"));
      assertThat(finalState.uploadImageTaskId, nullValue());
      assertThat(finalState.uploadImagePollCount, is(0));
    }

    @Test
    public void testUploadImageFailureExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception in getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("UPLOAD_IMAGE_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in getTaskAsync call"));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(1));
    }

    @Test
    public void testImageInErrorState() throws Throwable {

      doAnswer(MockHelper.mockGetImageAsync(imageId, ImageState.ERROR))
          .when(imagesApi)
          .getImageAsync(eq(imageId), Matchers.<FutureCallback<Image>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Image " + imageId + " reached ERROR state"));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingPollCount, is(1));
    }

    @Test
    public void testImageInPendingDeleteState() throws Throwable {

      doAnswer(MockHelper.mockGetImageAsync(imageId, ImageState.PENDING_DELETE))
          .when(imagesApi)
          .getImageAsync(eq(imageId), Matchers.<FutureCallback<Image>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Image " + imageId +
          " reached unexpected state PENDING_DELETE"));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingPollCount, is(1));
      assertThat(finalState.imageSeedingProgress, nullValue());
    }

    @Test
    public void testGetImageFailureExceptionInGetImageCall() throws Throwable {

      doThrow(new IOException("I/O exception in getImageAsync call"))
          .when(imagesApi)
          .getImageAsync(eq(imageId), Matchers.<FutureCallback<Image>>any());

      UploadImageTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in getImageAsync call"));
      assertThat(finalState.uploadImageTaskId, is("UPLOAD_IMAGE_TASK_ID"));
      assertThat(finalState.uploadImagePollCount, is(3));
      assertThat(finalState.imageId, is(imageId));
      assertThat(finalState.imageSeedingPollCount, is(1));
      assertThat(finalState.imageSeedingProgress, nullValue());
    }
  }

  private UploadImageTaskService.State buildValidStartState(
      @Nullable TaskState.TaskStage taskStage,
      @Nullable UploadImageTaskService.TaskState.SubStage subStage) {
    UploadImageTaskService.State startState = new UploadImageTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskPollDelay = 10;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.imageName = "IMAGE_NAME";
    startState.imageFile = "IMAGE_FILE";
    if (taskStage != null) {
      startState.taskState = new UploadImageTaskService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
