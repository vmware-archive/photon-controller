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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.VibService;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

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
   * This class implements tests for object initialization.
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
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION)));
    }
  }

  /**
   * This class implements tests for the handleStart method.
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
        // Exceptions are expected in the case where a service was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testHost != null) {
        TestHost.destroy(testHost);
        testHost = null;
      }
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(TaskState.TaskStage taskStage) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(taskStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.vibServiceLink, is("VIB_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage taskStage) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(taskStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(null);
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
   * This class implements tests for the handlePatch method.
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
      if (testHost != null) {
        TestHost.destroy(testHost);
        testHost = null;
      }
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(startStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadVibTaskService.buildPatch(patchStage, null));

      op = testHost.sendRequestAndWait(patchOperation);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(null);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(startStage);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(UploadVibTaskService.buildPatch(patchStage, null));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(null);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(null);
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State patchState = UploadVibTaskService.buildPatch(TaskState.TaskStage.STARTED, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, getDefaultAttributeFieldValue(declaredField, null));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UploadVibTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final File sourceDirectory = new File("/tmp/deployAgent/vibs");

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
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
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
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
      doReturn(httpFileServiceClient).when(httpFileServiceClientFactory).create(anyString(), anyString(), anyString());

      doReturn(
          new Callable<Integer>() {
            @Override
            public Integer call() {
              return HttpsURLConnection.HTTP_OK;
            }
          })
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VibService.State.class);

      hostStartState = TestHelper.createHostService(cloudStoreEnvironment, UsageTag.MGMT);
      vibStartState = TestHelper.createVibService(testEnvironment, hostStartState, sourceFile);

      startState = buildValidStartState(null);
      startState.controlFlags = null;
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

      Mockito.verify(httpFileServiceClient).uploadFile(
          eq(sourceFile.getAbsolutePath()),
          eq("/tmp/" + ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink) + "/source.vib"),
          eq(false));

      Mockito.verifyNoMoreInteractions(httpFileServiceClient);

      VibService.State vibState = testEnvironment.getServiceState(vibStartState.documentSelfLink,
          VibService.State.class);
      assertThat(vibState.uploadPath, is("/tmp/" +
          ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink) + "/source.vib"));
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

      Mockito.verify(httpFileServiceClient).uploadFile(
          eq(sourceFile.getAbsolutePath()),
          eq("/tmp/" + ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink) + "/source.vib"),
          eq(false));

      Mockito.verifyNoMoreInteractions(httpFileServiceClient);

      VibService.State vibState = testEnvironment.getServiceState(vibStartState.documentSelfLink,
          VibService.State.class);
      assertThat(vibState.uploadPath, is("/tmp/" +
          ServiceUtils.getIDFromDocumentSelfLink(vibStartState.documentSelfLink) + "/source.vib"));
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
  }

  private Object getDefaultAttributeFieldValue(Field declaredField, Object defaultValue) throws Throwable {
    return (defaultValue != null) ? defaultValue : ReflectionUtils.getDefaultAttributeValue(declaredField);
  }

  private UploadVibTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    UploadVibTaskService.State startState = new UploadVibTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.vibServiceLink = "VIB_SERVICE_LINK";

    if (taskStage != null) {
      startState.taskState = new TaskState();
      startState.taskState.stage = taskStage;
    }

    return startState;
  }
}
