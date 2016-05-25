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

import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerContextTest;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerService.State;
import com.vmware.photon.controller.deployer.dcp.util.Pair;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashSet;

/**
 * This class implements tests for the {@link MigrationStatusUpdateTriggerService}
 * class.
 */
public class MigrationStatusUpdateTriggerServiceTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private MigrationStatusUpdateTriggerService service;

    @BeforeMethod
    public void setUpTest() {
      service = new MigrationStatusUpdateTriggerService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * This class implements test for the handleStart method.
   */
  public class HandleStartTest {
    private MigrationStatusUpdateTriggerService service;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new MigrationStatusUpdateTriggerService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      MigrationStatusUpdateTriggerService.State startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              MigrationStatusUpdateTriggerService.State.class, NotNull.class));
    }

    private void startService(MigrationStatusUpdateTriggerService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * This class implements test for End2End.
   */
  public class EndToEndTest {
    private MigrationStatusUpdateTriggerService.State startState;
    private TestEnvironment testEnvironment = null;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine = null;
    private DeployerContext deployerContext;

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidStartState();
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          DeployerContextTest.class.getResource("/config.yml").getPath()).getDeployerContext();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void successWithFinishedTasks() throws Throwable {
      startTestEnvironment();
      createFinishedCopystateTaskService(testEnvironment);
      createFinishedUploadVibTaskService(testEnvironment);

      Operation postResult = testEnvironment
          .sendPostAndWait(MigrationStatusUpdateTriggerFactoryService.SELF_LINK, startState);
      State state = postResult.getBody(MigrationStatusUpdateTriggerService.State.class);

      testEnvironment.sendPatchAndWait(state.documentSelfLink, new MigrationStatusUpdateTriggerService.State());
      Thread.sleep(1000);

      DeploymentService.State deploymentState = cloudStoreMachine
          .getServiceState(state.deploymentServiceLink, DeploymentService.State.class);
      assertThat(deploymentState.dataMigrationProgress.size(), is(MigrationUtils.findAllUpgradeServices().size()));
      assertThat(
          deploymentState.dataMigrationProgress
              .get(MigrationUtils.findAllUpgradeServices().iterator().next().sourceFactoryServicePath + "/"),
          is(1));
      assertThat(deploymentState.vibsUploaded, is(1L));
      assertThat(deploymentState.vibsUploading, is(0L));
    }

    @Test
    public void successWithoutFinishedTasks() throws Throwable {
      startTestEnvironment();
      createRunningCopystateTaskService(testEnvironment);
      createRunningUploadVibTaskService(testEnvironment);

      Operation postResult = testEnvironment
          .sendPostAndWait(MigrationStatusUpdateTriggerFactoryService.SELF_LINK, startState);
      State state = postResult.getBody(MigrationStatusUpdateTriggerService.State.class);

      testEnvironment.sendPatchAndWait(state.documentSelfLink, new MigrationStatusUpdateTriggerService.State());
      Thread.sleep(1000);

      DeploymentService.State deploymentState = cloudStoreMachine
          .getServiceState(state.deploymentServiceLink, DeploymentService.State.class);
      assertThat(deploymentState.dataMigrationProgress.size(), is(MigrationUtils.findAllUpgradeServices().size()));
      assertThat(deploymentState.dataMigrationProgress.values().stream().mapToInt(value -> value).sum(), is(0));
      assertThat(deploymentState.vibsUploaded, is(0L));
      assertThat(deploymentState.vibsUploading, is(1L));
    }

    private void startTestEnvironment() {
      try {
        testEnvironment = new TestEnvironment.Builder()
            .deployerContext(deployerContext)
            .cloudServerSet(cloudStoreMachine.getServerSet())
            .hostCount(1)
            .build();
        startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    private void createFinishedCopystateTaskService(TestEnvironment testEnvironment) throws Throwable {
      testEnvironment.sendPostAndWait(CopyStateTaskFactoryService.SELF_LINK, buildCopyStateState(TaskStage.FINISHED));
    }

    private void createRunningCopystateTaskService(TestEnvironment testEnvironment) throws Throwable {
      testEnvironment.sendPostAndWait(CopyStateTaskFactoryService.SELF_LINK, buildCopyStateState(TaskStage.STARTED));
    }

    private void createFinishedUploadVibTaskService(TestEnvironment testEnvironment) throws Throwable {
      testEnvironment.sendPostAndWait(UploadVibTaskFactoryService.SELF_LINK, buildUploadVibState(TaskStage.FINISHED));
    }

    private void createRunningUploadVibTaskService(TestEnvironment testEnvironment) throws Throwable {
      testEnvironment.sendPostAndWait(UploadVibTaskFactoryService.SELF_LINK, buildUploadVibState(TaskStage.STARTED));
    }

    private CopyStateTaskService.State buildCopyStateState(TaskStage stage) {
      CopyStateTaskService.State startState = new CopyStateTaskService.State();
      startState.taskState = new TaskState();
      startState.taskState.stage = stage;
      startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
      startState.destinationPort = 4321;
      startState.destinationIp = "127.0.0.1";
      startState.sourceServers = new HashSet<>();
      startState.sourceServers.add(new Pair<>("127.0.0.1", 1234));
      startState.factoryLink = MigrationUtils.findAllUpgradeServices().iterator().next().destinationFactoryServicePath;
      startState.sourceFactoryLink = MigrationUtils.findAllUpgradeServices().iterator().next().sourceFactoryServicePath;
      return startState;
    }

    private ServiceDocument buildUploadVibState(TaskStage stage) {
      UploadVibTaskService.State startState = new UploadVibTaskService.State();
      startState.taskState = new UploadVibTaskService.TaskState();
      startState.taskState.stage = stage;

      if (stage == TaskStage.STARTED) {
        startState.taskState.subStage = UploadVibTaskService.TaskState.SubStage.UPLOAD_VIB;
      }

      startState.taskControlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
      startState.workQueueServiceLink = "link";
      startState.vibServiceLink = "link";
      return startState;
    }
  }

  private State buildValidStartState() {
    MigrationStatusUpdateTriggerService.State state = new MigrationStatusUpdateTriggerService.State();
    state.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/test-uuid";
    return state;
  }
}
