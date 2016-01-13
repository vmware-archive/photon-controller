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

import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.AgentStatusCode;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.TException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
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
      assertThat(serviceState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
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
          {"maximumPollCount", new Integer(1)},
          {"pollInterval", new Integer(1)},
          {"pollCount", null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
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

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = DcpRuntimeException.class)
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
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
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

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},

          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State patchState = ProvisionHostTaskService.buildPatch(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.INSTALL_AGENT, null);
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
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT));
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
   * This class implements tests for the WAIT_FOR_AGENT sub-stage.
   */
  public class WaitForAgentTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT);
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
    public void testWaitForAgentSuccess() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentSuccessAfterFailures() throws Throwable {

      HostClientMock agentNotReadyMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      HostClientMock agentRestartingMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      HostClientMock agentReadyMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(hostClientFactory.create())
          .thenReturn(agentNotReadyMock)
          .thenReturn(agentRestartingMock)
          .thenReturn(agentReadyMock);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionHostTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentFailureWithInvalidResult() throws Throwable {

      HostClientMock agentRestartingMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      doReturn(agentRestartingMock).when(hostClientFactory).create();

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

      HostClientMock agentNotReadyMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during agent status call"))
          .build();

      doReturn(agentNotReadyMock).when(hostClientFactory).create();

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
    private HostClientFactory hostClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT);
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
    public void testProvisionAgentSuccess() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionHostTaskService.TaskState.SubStage.PROVISION_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testProvisionAgentFailure() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.INVALID_CONFIG)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

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
   * This class implements tests for the GET_HOST_CONFIG sub-stage.
   */
  public class GetHostConfigTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private List<Datastore> datastoreList;
    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private Set<String> imageDatastoreIds;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.GET_HOST_CONFIG);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;

      datastoreList = buildDatastoreList(10);

      imageDatastoreIds = datastoreList.stream()
          .limit(3)
          .map((datastore) -> datastore.getId())
          .collect(Collectors.toSet());
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      assertNoServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      deleteServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
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
    public void testGetHostConfigSuccessWithNoDatastores() throws Throwable {
      testGetHostConfigSuccess();
    }

    @Test
    public void testGetHostConfigSuccessWithExistingDatastores() throws Throwable {

      for (Datastore datastore : datastoreList) {
        DatastoreService.State datastoreState = new DatastoreService.State();
        datastoreState.id = datastore.getId();
        datastoreState.name = datastore.getName();
        datastoreState.tags = datastore.getTags();
        datastoreState.type = datastore.getType().name();
        datastoreState.documentSelfLink = datastore.getId();
        Operation op = cloudStoreEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, datastoreState);
        assertThat(op.getStatusCode(), is(200));
      }

      testGetHostConfigSuccess();
    }

    private void testGetHostConfigSuccess() throws Throwable {

      HostConfig hostConfig = new HostConfig();
      hostConfig.setDatastores(datastoreList);
      hostConfig.setImage_datastore_ids(imageDatastoreIds);
      hostConfig.setCpu_count(4);
      hostConfig.setMemory_mb(8192);

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.OK)
          .hostConfig(hostConfig)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));

      List<DatastoreService.State> datastoreStates = getServicesOfType(cloudStoreEnvironment,
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

      assertThat(hostState.cpuCount, is(4));
      assertThat(hostState.memoryMb, is(8192));
    }

    @Test
    public void testGetHostConfigFailureSystemErrorResponse() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.SYSTEM_ERROR)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, nullValue());
    }

    @Test
    public void testGetHostConfigFailureThriftException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigFailure(new TException("Thrift exception when contacting host"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Thrift exception when contacting host"));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private List<Datastore> datastoreList;
    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private Set<String> imageDatastoreIds;
    private ListeningExecutorService listeningExecutorService;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {

      FileUtils.deleteDirectory(storageDirectory);

      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      datastoreList = buildDatastoreList(10);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      hostClientFactory = mock(HostClientFactory.class);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      imageDatastoreIds = datastoreList.stream()
          .limit(3)
          .map((datastore) -> datastore.getId())
          .collect(Collectors.toSet());

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
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
      assertNoServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()), HostState.NOT_PROVISIONED).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
      deleteServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
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
    public void testEndToEndSuccess() throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.SCRIPT_NAME, true);

      HostConfig hostConfig = new HostConfig();
      hostConfig.setDatastores(datastoreList);
      hostConfig.setImage_datastore_ids(imageDatastoreIds);
      hostConfig.setCpu_count(4);
      hostConfig.setMemory_mb(8192);

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .getConfigResultCode(GetConfigResultCode.OK)
          .hostConfig(hostConfig)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      List<DatastoreService.State> datastoreStates = getServicesOfType(cloudStoreEnvironment,
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

      assertThat(hostState.cpuCount, is(4));
      assertThat(hostState.memoryMb, is(8192));
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

  private <T extends ServiceDocument> List<String> getServiceLinksOfType(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create().addKindFieldClause(clazz).build())
        .build();

    return testEnvironment.sendQueryAndWait(queryTask).results.documentLinks;
  }

  private <T extends ServiceDocument> List<T> getServicesOfType(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    QueryTask queryResult = testEnvironment.sendQueryAndWait(QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create().addKindFieldClause(clazz).build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build());

    List<T> returnValue = new ArrayList<>(queryResult.results.documentLinks.size());
    for (String documentLink : queryResult.results.documentLinks) {
      returnValue.add(Utils.fromJson(queryResult.results.documents.get(documentLink), clazz));
    }

    return returnValue;
  }

  private <T extends ServiceDocument> void assertNoServicesOfType(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    List<String> documentLinks = getServiceLinksOfType(testEnvironment, clazz);
    assertThat(documentLinks.size(), is(0));
  }

  private <T extends ServiceDocument> void deleteServicesOfType(
      com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment testEnvironment, Class<T> clazz)
      throws Throwable {

    List<String> documentLinks = getServiceLinksOfType(testEnvironment, clazz);
    if (documentLinks.size() > 0) {
      for (String documentLink : documentLinks) {
        testEnvironment.sendDeleteAndWait(documentLink);
      }
    }
  }

  private List<Datastore> buildDatastoreList(int count) {
    List<Datastore> returnValue = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Datastore datastore = new Datastore("datastore-id-" + Integer.toString(i));
      datastore.setName("datastore-name-" + Integer.toString(i));
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
