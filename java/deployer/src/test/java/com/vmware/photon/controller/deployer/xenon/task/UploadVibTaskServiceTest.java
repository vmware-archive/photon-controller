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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.xenon.entity.VibService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link UploadVibTaskService} class.
 */
public class UploadVibTaskServiceTest {

  /**
   * Dummy test case to make IntelliJ recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for {@link UploadVibTaskService} object initialization.
   */
  public class InitializationTest {

    private UploadVibTaskService uploadVibTaskService;

    @BeforeMethod
    public void setUpTest() {
      uploadVibTaskService = new UploadVibTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(uploadVibTaskService.getOptions(), is(EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE)));
    }
  }

  /**
   * This class implements tests for the {@link UploadVibTaskService#handleStart(Operation)} method.
   */
  public class HandleStartTest {

    private TestHost testHost;
    private UploadVibTaskService uploadVibTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      uploadVibTaskService = new UploadVibTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage taskStage, UploadVibTaskService.TaskState.SubStage subStage)
        throws Throwable {

      UploadVibTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskControlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.taskTimeoutMicros, is(0L));
      assertThat(serviceState.workQueueServiceLink, is("WORK_QUEUE_SERVICE_LINK"));
      assertThat(serviceState.vibServiceLink, is("VIB_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return TestHelper.getValidStartStages(UploadVibTaskService.TaskState.SubStage.class);
    }

    @Test
    public void testCreateWithDefaultTimeout() throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(null, null);
      startState.taskTimeoutMicros = null;
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskTimeoutMicros, is(TimeUnit.MINUTES.toMicros(10)));

      assertThat(uploadVibTaskService.getOptions(), hasItem(Service.ServiceOption.PERIODIC_MAINTENANCE));
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(uploadVibTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadVibTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link UploadVibTaskService#handlePatch(Operation)} method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private UploadVibTaskService uploadVibTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      uploadVibTaskService = new UploadVibTaskService();
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
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         UploadVibTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchState,
                                         UploadVibTaskService.TaskState.SubStage patchSubStage) throws Throwable {

      UploadVibTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      op = testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadVibTaskService.buildPatch(patchState, patchSubStage)));

      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchState));

      //
      // N.B. The BEGIN_EXECUTION start state is automatically transitioned to the UPLOAD_VIB sub-
      // stage in order to prevent multiple start patches from the work queue service.
      //

      if (patchSubStage == UploadVibTaskService.TaskState.SubStage.BEGIN_EXECUTION) {
        assertThat(serviceState.taskState.subStage, is(UploadVibTaskService.TaskState.SubStage.UPLOAD_VIB));
      } else {
        assertThat(serviceState.taskState.subStage, is(patchSubStage));
      }
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(UploadVibTaskService.TaskState.SubStage.class);
    }

    @Test
    public void testDoubleStartPatch() throws Throwable {

      UploadVibTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State patchState = UploadVibTaskService.buildPatch(TaskState.TaskStage.STARTED,
          UploadVibTaskService.TaskState.SubStage.BEGIN_EXECUTION);

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(UploadVibTaskService.TaskState.SubStage.UPLOAD_VIB));

      try {
        testHost.sendRequestAndWait(Operation
            .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
            .setBody(patchState));
        fail("Expected bad request exception when sending a second start patch");
      } catch (BadRequestException e) {
        // Nothing
      }
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           UploadVibTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           UploadVibTaskService.TaskState.SubStage patchSubStage) throws Throwable {

      UploadVibTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadVibTaskService.buildPatch(patchStage, patchSubStage)));
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(UploadVibTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {

      UploadVibTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State patchState = UploadVibTaskService.buildPatch(TaskState.TaskStage.STARTED,
          UploadVibTaskService.TaskState.SubStage.BEGIN_EXECUTION);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadVibTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link UploadVibTaskService}.
   */
  public class EndToEndTest {

    private final File sourceDirectory = new File("/tmp/deployAgent/vibs");

    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private HostService.State hostStartState;
    private HttpFileServiceClient httpFileServiceClient;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private File sourceFile;
    private UploadVibTaskService.State startState;
    private TestEnvironment testEnvironment;
    private VibService.State vibStartState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource("/config.yml").getPath()).getDeployerContext();
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostCount(1)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      FileUtils.deleteDirectory(sourceDirectory);
      assertTrue(sourceDirectory.mkdirs());
      sourceFile = TestHelper.createSourceFile("source.vib", sourceDirectory);

      httpFileServiceClient = mock(HttpFileServiceClient.class);

      doReturn(
          new Callable<Integer>() {
            @Override
            public Integer call() {
              return HttpsURLConnection.HTTP_OK;
            }
          })
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      doReturn(httpFileServiceClient)
          .when(httpFileServiceClientFactory)
          .create(anyString(), anyString(), anyString());

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      hostStartState = TestHelper.createHostService(cloudStoreEnvironment, UsageTag.MGMT);

      TestHelper.assertNoServicesOfType(testEnvironment, VibService.State.class);
      vibStartState = TestHelper.createVibService(testEnvironment, hostStartState, sourceFile);

      startState = buildValidStartState(null, null);
      startState.taskControlFlags = null;
      startState.taskTimeoutMicros = null;
      startState.workQueueServiceLink = DeployerXenonServiceHost.UPLOAD_VIB_WORK_QUEUE_SELF_LINK;
      startState.vibServiceLink = vibStartState.documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VibService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(sourceDirectory);
    }

    @Test
    public void testSuccess() throws Throwable {

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      String uploadPath = UriUtils.buildUriPath("tmp", "photon-controller-vibs",
          ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink), "source.vib");

      verify(httpFileServiceClient).uploadFile(
          eq(sourceFile.getAbsolutePath()),
          eq(uploadPath),
          eq(false));

      verifyNoMoreInteractions(httpFileServiceClient);

      VibService.State vibState = testEnvironment.getServiceState(vibStartState.documentSelfLink,
          VibService.State.class);
      assertThat(vibState.uploadPath, is(uploadPath));
    }

    @Test
    public void testSuccessWithMultipleVibs() throws Throwable {

      TestHelper.createSourceFile("vib1.vib", sourceDirectory);
      TestHelper.createSourceFile("vib2.vib", sourceDirectory);
      TestHelper.createSourceFile("vib3.vib", sourceDirectory);

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      String uploadPath = UriUtils.buildUriPath("tmp", "photon-controller-vibs",
          ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink), "source.vib");

      verify(httpFileServiceClient).uploadFile(
          eq(sourceFile.getAbsolutePath()),
          eq(uploadPath),
          eq(false));

      verifyNoMoreInteractions(httpFileServiceClient);

      VibService.State vibState = testEnvironment.getServiceState(vibStartState.documentSelfLink,
          VibService.State.class);
      assertThat(vibState.uploadPath, is(uploadPath));
    }

    @Test
    public void testFailureMissingSourceDirectory() throws Throwable {

      FileUtils.deleteDirectory(sourceDirectory);

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Invalid VIB source directory " + sourceDirectory));
    }

    @Test
    public void testFailureMissingSourceVib() throws Throwable {

      FileUtils.deleteDirectory(sourceDirectory);
      assertTrue(sourceDirectory.mkdirs());

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Invalid VIB source file " + sourceFile));
    }

    @Test
    public void testFailureHttpReturnCode() throws Throwable {

      doReturn(
          new Callable<Integer>() {
            @Override
            public Integer call() {
              return HttpsURLConnection.HTTP_BAD_REQUEST;
            }
          })
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("Unexpected HTTP result 400 when uploading VIB file source.vib to host hostAddress"));
    }

    @Test
    public void testFailureIOExceptionDuringUpload() throws Throwable {

      doReturn(
          new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
              throw new IOException("I/O exception during upload");
            }
          })
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("I/O exception during upload"));
    }

    @Test
    public void testTimeoutFailure() throws Throwable {

      startState.taskTimeoutMicros = TimeUnit.SECONDS.toMicros(1);

      doReturn(
          new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
              Thread.sleep(TimeUnit.SECONDS.toMillis(5));
              return HttpsURLConnection.HTTP_OK;
            }
          })
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(408));
      assertThat(finalState.taskState.failure.message, nullValue());
    }
  }

  private UploadVibTaskService.State buildValidStartState(TaskState.TaskStage stage,
                                                          UploadVibTaskService.TaskState.SubStage subStage) {

    UploadVibTaskService.State startState = new UploadVibTaskService.State();
    startState.taskControlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskTimeoutMicros = 0L;
    startState.workQueueServiceLink = "WORK_QUEUE_SERVICE_LINK";
    startState.vibServiceLink = "VIB_SERVICE_LINK";

    if (stage != null) {
      startState.taskState = new UploadVibTaskService.TaskState();
      startState.taskState.stage = stage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
