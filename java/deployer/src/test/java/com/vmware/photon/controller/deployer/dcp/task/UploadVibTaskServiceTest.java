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
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
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

    @Test(dataProvider = "OptionalFieldNames")
    public void testOptionalFieldValuePersisted(String fieldName, Object defaultValue) throws Throwable {
      UploadVibTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, getDefaultAttributeFieldValue(declaredField, defaultValue));
      Operation op = testHost.startServiceSynchronously(uploadVibTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      UploadVibTaskService.State serviceState = testHost.getServiceState(UploadVibTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(getDefaultAttributeFieldValue(declaredField, defaultValue)));
    }

    @DataProvider(name = "OptionalFieldNames")
    public Object[][] getOptionalFieldNames() {
      Map<String, String> vibPaths = new HashMap<>();
      vibPaths.put("VIB_NAME", "VIB_PATH");
      return new Object[][]{
          {"vibPaths", new HashMap<>()},
          {"vibPaths", vibPaths},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
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
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
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
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = DcpRuntimeException.class)
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

    private static final String configFilePath = "/config.yml";

    private final File destinationDirectory = new File("/tmp/deployAgent/output");
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private UploadVibTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostCount(1)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .build();

      startState = buildValidStartState(null);
      startState.controlFlags = null;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      assertTrue(destinationDirectory.mkdirs());
      assertTrue(vibDirectory.mkdirs());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(destinationDirectory);
      FileUtils.deleteDirectory(vibDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (cloudStoreEnvironment != null) {
        cloudStoreEnvironment.stop();
        cloudStoreEnvironment = null;
      }

      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);
    }

    @Test(dataProvider = "VibCounts")
    public void testEndToEndSuccess(int vibCount) throws Throwable {
      Set<String> vibFiles = new HashSet<>(vibCount);
      for (int i = 0; i < vibCount; i++) {
        File sourceFile = TestHelper.createSourceFile("vib" + i + ".vib", vibDirectory);
        vibFiles.add(sourceFile.getName());
      }

      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);

      UploadVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UploadVibTaskFactoryService.SELF_LINK,
              startState,
              UploadVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.vibPaths.keySet(), containsInAnyOrder(vibFiles.toArray()));
    }

    @DataProvider(name = "VibCounts")
    public Object[][] getVibCounts() {
      return new Object[][]{
          {new Integer(1)},
          {new Integer(3)},
      };
    }
  }

  private Object getDefaultAttributeFieldValue(Field declaredField, Object defaultValue) throws Throwable {
    return (defaultValue != null) ? defaultValue : ReflectionUtils.getDefaultAttributeValue(declaredField);
  }

  private UploadVibTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    UploadVibTaskService.State startState = new UploadVibTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";

    if (taskStage != null) {
      startState.taskState = new TaskState();
      startState.taskState.stage = taskStage;
    }

    return startState;
  }
}
