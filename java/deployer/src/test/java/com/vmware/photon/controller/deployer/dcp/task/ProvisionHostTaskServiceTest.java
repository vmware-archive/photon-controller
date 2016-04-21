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

import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.AgentControlClientMock;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * This class implements tests for the {@link ProvisionHostTaskService} class.
 */
public class ProvisionHostTaskServiceTest {

  private final String configFilePath = "/config.yml";

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

    private ProvisionHostTaskService provisionHostTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(provisionHostTaskService.getOptions(), is(EnumSet.of(
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

    private ProvisionHostTaskService provisionHostTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
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

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage taskStage,
                                    ProvisionHostTaskService.TaskState.SubStage taskSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(taskStage, taskSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
      assertThat(serviceState.vibPath, is("VIB_PATH"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage taskStage,
                                           ProvisionHostTaskService.TaskState.SubStage taskSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(taskStage, taskSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage taskStage,
                                       ProvisionHostTaskService.TaskState.SubStage taskSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(taskStage, taskSubStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
      assertThat(serviceState.taskState.subStage, nullValue());
      assertThat(serviceState.controlFlags, is(0));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "OptionalFieldNames")
    public void testOptionalFieldValuesPersisted(String fieldName, Object defaultValue) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, getDefaultAttributeFieldValue(declaredField, defaultValue));
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(getDefaultAttributeFieldValue(declaredField, defaultValue)));
    }

    @DataProvider(name = "OptionalFieldNames")
    public Object[][] getOptionalFieldNames() {
      return new Object[][]{
          {"maximumPollCount", 1},
          {"pollInterval", 1},
          {"pollCount", null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(provisionHostTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostTaskService.State.class, NotNull.class));
    }

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateRequiredFieldNegative(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, -1);
      testHost.startServiceSynchronously(provisionHostTaskService, startState);
    }

    @DataProvider(name = "PositiveFieldNames")
    public Object[][] getPositiveFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostTaskService.State.class, Positive.class));
    }
  }

  /**
   * This class implements tests for the HandlePatch method.
   */
  public class HandlePatchTest {

    private ProvisionHostTaskService provisionHostTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
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
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         ProvisionHostTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         ProvisionHostTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionHostTaskService.buildPatch(patchStage, patchSubStage, null));
      assertThat(testHost.sendRequestAndWait(patchOperation).getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           ProvisionHostTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           ProvisionHostTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionHostTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State patchState = ProvisionHostTaskService.buildPatch(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK, null);
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
              ProvisionHostTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements tests for the INSTALL_AGENT sub-stage.
   */
  public class InstallAgentTest {

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private ListeningExecutorService listeningExecutorService;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostCount(1)
          .listeningExecutorService(listeningExecutorService)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      assertTrue(scriptDirectory.mkdirs());
      assertTrue(scriptLogDirectory.mkdirs());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
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

    @Test
    public void testInstallAgentSuccess() throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.SCRIPT_NAME, true);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testInstallAgentFailureNonZeroScriptExitCode() throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.SCRIPT_NAME, false);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("Deploying the agent to host hostAddress failed with exit code 1"));
    }

    @Test
    public void testInstallAgentFailureScriptRunnerException() throws Throwable {

      // Do not create the script file

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ProvisionHostTaskService.SCRIPT_NAME));
      assertThat(finalState.taskState.failure.message, containsString("No such file or directory"));
    }
  }

  /**
   * This class implements tests for the WAIT_FOR_INSTALLATION sub-stage.
   */
  public class WaitForAgentInstallTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private AgentControlClientFactory agentControlClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      agentControlClientFactory = mock(AgentControlClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .agentControlClientFactory(agentControlClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
      startState.maximumPollCount = 3;
      startState.pollInterval = 10;
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
    }

    @Test
    public void testWaitForAgentSuccess()
        throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      startState.taskState.subStage = ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION;
      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentSuccessAfterFailures()
        throws Throwable {

      AgentControlClientMock agentNotReadyMock = new AgentControlClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      AgentControlClientMock agentRestartingMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      AgentControlClientMock agentReadyMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(agentControlClientFactory.create())
          .thenReturn(agentNotReadyMock)
          .thenReturn(agentRestartingMock)
          .thenReturn(agentReadyMock);

      startState.taskState.subStage = ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION;
      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_INSTALLATION);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentFailureWithInvalidResult() throws Throwable {

      AgentControlClientMock agentRestartingMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      doReturn(agentRestartingMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after installation after 3 retries"));
    }

    @Test
    public void testWaitForAgentFailureWithException() throws Throwable {

      AgentControlClientMock agentNotReadyMock = new AgentControlClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      doReturn(agentNotReadyMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after installation after 3 retries"));
    }
  }

  /**
   * This class implements tests for the PROVISION_AGENT sub-stage.
   */
  public class ProvisionAgentTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
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
    }

    @Test
    public void testProvisionAgentSuccess() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testProvisionAgentFailure() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.INVALID_CONFIG)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "Provisioning the agent on host hostAddress failed with error"));
      assertThat(finalState.taskState.failure.message, containsString("InvalidAgentConfigurationException"));
    }
  }

  /**
   * This class implements tests for the WAIT_FOR_PROVISION sub-stage.
   */
  public class WaitForAgentProvisionTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private AgentControlClientFactory agentControlClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      agentControlClientFactory = mock(AgentControlClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .agentControlClientFactory(agentControlClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
      startState.maximumPollCount = 3;
      startState.pollInterval = 10;
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
    }

    @Test
    public void testWaitForAgentSuccess()
        throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      startState.taskState.subStage = ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION;
      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentSuccessAfterFailures()
        throws Throwable {

      AgentControlClientMock agentNotReadyMock = new AgentControlClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      AgentControlClientMock agentRestartingMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      AgentControlClientMock agentReadyMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(agentControlClientFactory.create())
          .thenReturn(agentNotReadyMock)
          .thenReturn(agentRestartingMock)
          .thenReturn(agentReadyMock);

      startState.taskState.subStage = ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION;
      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_PROVISION);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.UPDATE_HOST_STATE));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentFailureWithInvalidResult() throws Throwable {

      AgentControlClientMock agentRestartingMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      doReturn(agentRestartingMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }

    @Test
    public void testWaitForAgentFailureWithException() throws Throwable {

      AgentControlClientMock agentNotReadyMock = new AgentControlClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      doReturn(agentNotReadyMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }
  }

  /**
   * This class implements tests for the PROVISION_NETWORK sub-stage.
   */
  public class ProvisionNetworkTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private NsxClientFactory nsxClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;
    private String fabricNodeId;
    private String transportNodeId;

    public ProvisionNetworkTest() {
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      nsxClientFactory = mock(NsxClientFactory.class);
      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .nsxClientFactory(nsxClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.PROVISION_NETWORK);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment, false, true)
          .documentSelfLink;

      fabricNodeId = "fabricNodeId";
      transportNodeId = "transportNodeId";
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (cloudStoreEnvironment != null) {
        cloudStoreEnvironment.stop();
        cloudStoreEnvironment = null;
      }

      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test
    public void testProvisionNetworkSuccess() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .registerFabricNode(true, fabricNodeId)
          .createTransportNode(true, transportNodeId)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.stage == TaskState.TaskStage.STARTED &&
                  state.taskState.subStage == ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));

      HostService.State hostState = cloudStoreEnvironment.getServiceState(startState.hostServiceLink,
          HostService.State.class);
      assertEquals(hostState.nsxFabricNodeId, fabricNodeId);
      assertEquals(hostState.nsxTransportNodeId, transportNodeId);
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final int datastoreCount = 10;

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private List<Datastore> datastoreList;
    private DeployerContext deployerContext;
    private AgentControlClientFactory agentControlClientFactory;
    private Set<String> imageDatastoreIds;
    private ListeningExecutorService listeningExecutorService;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;
    private HostClient hostClient;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      HostClientFactory hostClientFactory = mock(HostClientFactory.class);
      this.hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();
      cloudStoreEnvironment = new com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .listeningExecutorService(listeningExecutorService)
          .build();

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      assertTrue(scriptDirectory.mkdirs());
      assertTrue(scriptLogDirectory.mkdirs());
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
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

    @DataProvider(name = "PreExistingDatastoreCount")
    public Object[][] getPreExistingDatastoreCount() {
      return new Object[][]{
          {0}, {1}, {datastoreCount}
      };
    }

    @Test(dataProvider = "PreExistingDatastoreCount")
    public void testEndToEndSuccess(int preExistingDatastoresCount) throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.SCRIPT_NAME, true);

      datastoreList = buildDatastoreList(preExistingDatastoresCount);
      imageDatastoreIds = datastoreList.stream()
          .limit(3)
          .map((datastore) -> datastore.getId())
          .collect(Collectors.toSet());
      mockHostConfigCall(datastoreList, imageDatastoreIds);

      // This is to make sure datastore update succeeds even if there is an already existing datastore document
      createDatastoreDocuments(datastoreList, preExistingDatastoresCount);

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      List<DatastoreService.State> datastoreStates = TestHelper.getServicesOfType(cloudStoreEnvironment,
          DatastoreService.State.class);

      assertThat(datastoreStates.stream().map((datastoreState) -> datastoreState.id).collect(Collectors.toSet()),
          containsInAnyOrder(datastoreList.stream().map((datastore) -> datastore.getId()).toArray()));
      assertThat(datastoreStates.stream().map((datastoreState) -> datastoreState.name).collect(Collectors.toSet()),
          containsInAnyOrder(datastoreList.stream().map((datastore) -> datastore.getName()).toArray()));

      HostService.State hostState = cloudStoreEnvironment.getServiceState(startState.hostServiceLink,
          HostService.State.class);

      assertThat(hostState.state, is(HostState.READY));

      assertThat(hostState.reportedDatastores, containsInAnyOrder(datastoreList.stream()
          .map((datastore) -> datastore.getId()).toArray()));
      assertThat(hostState.reportedImageDatastores, containsInAnyOrder(datastoreList.stream()
          .map((datastore) -> datastore.getId()).filter((id) -> imageDatastoreIds.contains(id)).toArray()));

      assertThat(hostState.datastoreServiceLinks.entrySet(), containsInAnyOrder(datastoreList.stream()
          .collect(Collectors.toMap(
              (datastore) -> datastore.getName(),
              (datastore) -> DatastoreServiceFactory.SELF_LINK + "/" + datastore.getId()))
          .entrySet().toArray()));

      assertThat(hostState.esxVersion, is("6.0"));
      assertThat(hostState.cpuCount, is(4));
      assertThat(hostState.memoryMb, is(8192));
    }

    private void mockHostConfigCall(List<Datastore> datastores, Set<String> imageDatastoreIds) throws Throwable {
      HostConfig hostConfig = new HostConfig();
      hostConfig.setDatastores(datastores);
      hostConfig.setImage_datastore_ids(imageDatastoreIds);
      hostConfig.setCpu_count(4);
      hostConfig.setMemory_mb(8192);
      hostConfig.setEsx_version("6.0");

      GetConfigResponse response = new GetConfigResponse(GetConfigResultCode.OK);
      response.setHostConfig(hostConfig);

      Host.AsyncClient.get_host_config_call call = mock(Host.AsyncClient.get_host_config_call.class);
      doReturn(response).when(call).getResult();

      doAnswer(invocation -> {
        ((AsyncMethodCallback<Host.AsyncClient.get_host_config_call>) invocation.getArguments()[0]).onComplete(call);
        return null;
      }).when(this.hostClient).getHostConfig(any(AsyncMethodCallback.class));
    }

    private void createDatastoreDocuments(List<Datastore> datastoreList, int count) throws Throwable {
      for (int i = 0; i < count; i++) {
        Datastore datastore = datastoreList.get(i);
        DatastoreService.State datastoreState = new DatastoreService.State();
        datastoreState.documentSelfLink = datastore.getId();
        datastoreState.id = datastore.getId();
        datastoreState.name = datastore.getName();
        datastoreState.type = datastore.getType().name();
        datastoreState.tags = datastore.getTags();
        Operation result = cloudStoreEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, datastoreState);
        assertThat(result.getStatusCode(), equalTo(200));
      }
    }

    private List<Datastore> buildDatastoreList(int count) {
      List<Datastore> returnValue = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String datastoreName = UUID.randomUUID().toString();
        Datastore datastore = new Datastore("datastore-id-" + datastoreName);
        datastore.setName("datastore-name-" + datastoreName);
        switch (i % 3) {
          case 0:
            datastore.setTags(Collections.singleton("tag1"));
            datastore.setType(DatastoreType.SHARED_VMFS);
            break;
          case 1:
            datastore.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            datastore.setType(DatastoreType.LOCAL_VMFS);
            break;
          case 2:
            // Don't set tags
            datastore.setType(DatastoreType.EXT3);
            break;
        }
        returnValue.add(datastore);
      }
      return returnValue;
    }
  }

  private Object getDefaultAttributeFieldValue(Field declaredField, Object defaultValue) throws Throwable {
    return (defaultValue != null) ? defaultValue : ReflectionUtils.getDefaultAttributeValue(declaredField);
  }

  private ProvisionHostTaskService.State buildValidStartState(ProvisionHostTaskService.TaskState.TaskStage stage,
                                                              ProvisionHostTaskService.TaskState.SubStage subStage) {
    ProvisionHostTaskService.State startState = new ProvisionHostTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.vibPath = "VIB_PATH";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (stage != null) {
      startState.taskState = new ProvisionHostTaskService.TaskState();
      startState.taskState.stage = stage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
