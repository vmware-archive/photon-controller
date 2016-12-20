/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This class implements tests for
 * {@link com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationService}.
 */
public class SubnetConfigurationServiceTest {

  private TestHost testHost;
  private SubnetConfigurationService taskService;
  private final String gateway = "1.2.3.4";
  private final String cidr = "1.2.3.4/16";
  private final String subnetId = "subnet1";

  /**
   * Dummy test case to make IntelliJ recognize this as a test class.
   */
  @Test
  public void dummy() {
  }

  /**
   * Tests the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new SubnetConfigurationService();
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
      SubnetConfigurationTask startState = buildValidState(taskStage, true,
          SubnetConfigurationTask.SubnetOperation.CREATE);
      Operation startOp = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(Operation.STATUS_CODE_OK));
      assertThat(startState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage taskStage) throws Throwable {
      SubnetConfigurationTask startState = buildValidState(taskStage, true,
          SubnetConfigurationTask.SubnetOperation.CREATE);
      Operation op = testHost.startServiceSynchronously(taskService, startState);
      assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

      SubnetConfigurationTask serviceState = testHost.getServiceState(SubnetConfigurationTask.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new SubnetConfigurationService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
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
      SubnetConfigurationTask startState = buildValidState(startStage, true,
          SubnetConfigurationTask.SubnetOperation.CREATE);
      Operation op = testHost.startServiceSynchronously(taskService, startState);
      assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(taskService.buildPatch(patchStage, null));

      op = testHost.sendRequestAndWait(patchOperation);
      assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

      SubnetConfigurationTask serviceState = testHost.getServiceState(SubnetConfigurationTask.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
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

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      SubnetConfigurationTask startState = buildValidState(startStage, true,
          SubnetConfigurationTask.SubnetOperation.CREATE);
      Operation op = testHost.startServiceSynchronously(taskService, startState);
      assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(taskService.buildPatch(patchStage, null));

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
  }

  private SubnetConfigurationTask buildValidState(TaskState.TaskStage stage,
                                                  boolean isProcessingDisabled,
                                                  SubnetConfigurationTask.SubnetOperation subnetOperation) {
    SubnetConfigurationTask subnetIPLeaseTask = new SubnetConfigurationTask();
    subnetIPLeaseTask.subnetConfiguration = new SubnetConfigurationTask.SubnetConfiguration();
    subnetIPLeaseTask.subnetConfiguration.subnetId = subnetId;
    subnetIPLeaseTask.subnetConfiguration.subnetOperation = subnetOperation;

    if (subnetOperation == SubnetConfigurationTask.SubnetOperation.CREATE) {
      subnetIPLeaseTask.subnetConfiguration.subnetGateway = gateway;
      subnetIPLeaseTask.subnetConfiguration.subnetCidr = cidr;
    }

    if (stage != null) {
      subnetIPLeaseTask.taskState = new TaskState();
      subnetIPLeaseTask.taskState.stage = stage;
    }

    if (isProcessingDisabled) {
      subnetIPLeaseTask.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    }

    return subnetIPLeaseTask;
  }
}
