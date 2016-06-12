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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService.State;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.SampleService;
import com.vmware.photon.controller.deployer.dcp.entity.SampleServiceFactory;
import com.vmware.photon.controller.deployer.dcp.util.Pair;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements tests for the {@link CopyStateTaskService} class.
 */
public class CopyStateTaskServiceTest {
  private CopyStateTaskService service;
  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object which is sufficient to create a new
   * CopyStateTaskService instance.
   */
  private CopyStateTaskService.State buildValidStartState(TaskState.TaskStage stage) {
    CopyStateTaskService.State startState = new CopyStateTaskService.State();
    startState.taskState = new TaskState();
    startState.taskState.stage = stage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.sourceServers = new HashSet<>();
    startState.sourceServers.add(new Pair<>("127.0.0.1", new Integer(1234)));
    startState.destinationPort = 4321;
    startState.destinationIp = "127.0.0.1";
    startState.factoryLink = ContainerTemplateFactoryService.SELF_LINK;
    startState.sourceFactoryLink = ContainerTemplateFactoryService.SELF_LINK;
    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * CopyStateTaskService instance.
   */
  private CopyStateTaskService.State buildValidPatchState(TaskState.TaskStage stage) {
    CopyStateTaskService.State patchState = new CopyStateTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;
    return patchState;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      service = new CopyStateTaskService();
    }

    @AfterMethod
    public void tearDownTest() {
      service = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CopyStateTaskService();
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
    public void testValidStartStages(TaskState.TaskStage startStage) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));
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

    @Test(dataProvider = "StartStagesNotChanged")
    public void testStartStagesNotChanged(TaskState.TaskStage startStage) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      CopyStateTaskService.State savedState = testHost.getServiceState(CopyStateTaskService.State.class);
      assertThat(savedState.taskState.stage, is(startStage));
    }

    @DataProvider(name = "StartStagesNotChanged")
    public Object[][] getStartStagesNotChanged() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CopyStateTaskService.State.class,
              NotNull.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CopyStateTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CopyStateTaskService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));
      AddCloudHostWorkflowService.State savedState = testHost.getServiceState(AddCloudHostWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
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

    @Test(dataProvider = "InvalidStageUpdates")
    public void testInvalidStageUpdates(
        TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CopyStateTaskService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOperation);
        fail("Stage transition from " + startStage.toString() + " to " + patchStage.toString() + " should fail");
      } catch (XenonRuntimeException e) {
        // N.B. An assertion can be added here if an error message is added to
        //      the checkState calls in validatePatch.
      }
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
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

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithInvalidValue")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      CopyStateTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOperation = testHost.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CopyStateTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "fieldNamesWithInvalidValue")
    public Object[][] getFieldNamesWithInvalidValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CopyStateTaskService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the copy state task.
   */
  public class EndToEndTest {

    public static final int DOCUMENT_COUNT = 10;
    TestEnvironment sourceCluster;
    TestEnvironment destinationCluster;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment sourceCloudStore;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment destinationCloudStore;
    private CopyStateTaskService.State copyStateTaskServiceState;

    @BeforeMethod
    public void setupMethod() {
      copyStateTaskServiceState = buildValidStartState(TaskState.TaskStage.CREATED);
      copyStateTaskServiceState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownMethod() throws Throwable {
      if (sourceCluster != null) {
        sourceCluster.stop();
      }
      if (destinationCluster != null) {
        destinationCluster.stop();
      }

      if (null != sourceCloudStore) {
        sourceCloudStore.stop();
        sourceCloudStore = null;
      }

      if (null != destinationCloudStore) {
        destinationCloudStore.stop();
        destinationCloudStore = null;
      }
    }

    @Test(dataProvider = "hostCounts")
    public void successRunningOnSource(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);

      createDocuments(sourceCluster, DOCUMENT_COUNT);

      CopyStateTaskService.State finalState = sourceCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(getDocumentLinks(destinationCluster).size(), is(DOCUMENT_COUNT));
    }

    @Test(dataProvider = "hostCounts")
    public void successRunningOnDestination(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);

      createDocuments(sourceCluster, DOCUMENT_COUNT);

      CopyStateTaskService.State finalState = destinationCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(getDocumentLinks(sourceCluster).size(), is(DOCUMENT_COUNT));
    }

    @Test(dataProvider = "hostCounts")
    public void successTransformingHostDocuments(
        Integer sourceHostCount,
        Integer destinationHostCount) throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);

      sourceCluster.startFactoryServiceSynchronously(HostServiceFactory.class, HostServiceFactory.SELF_LINK);
      destinationCluster.startFactoryServiceSynchronously(HostServiceFactory.class, HostServiceFactory.SELF_LINK);

      Map<String, String> metaData = new HashMap<>();
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "dataStore1");
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "1.1.1.1");
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "1.1.1.1");
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "1.1.1.1");
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "1.1.1.1");
      metaData.put(State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "PortGroup");


      HostService.State host = ReflectionUtils.buildValidStartState(HostService.State.class);
      host.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name(), UsageTag.CLOUD.name()));
      host.metadata = metaData;
      HostService.State cts = TestHelper.createHostService(sourceCluster, host);
      copyStateTaskServiceState.factoryLink = HostServiceFactory.SELF_LINK;
      copyStateTaskServiceState.sourceFactoryLink = HostServiceFactory.SELF_LINK;
      copyStateTaskServiceState.performHostTransformation = Boolean.TRUE;

      CopyStateTaskService.State finalState = destinationCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      HostService.State serviceState =
          destinationCluster.getServiceState(cts.documentSelfLink, HostService.State.class);
      assertThat(serviceState.usageTags.size(), is(1));
      assertThat(serviceState.usageTags.iterator().next(), is(UsageTag.CLOUD.name()));
    }

    @Test(dataProvider = "hostCounts")
    public void successWhenDocumentsAlreadyExistsOnDestination(Integer sourceHostCount, Integer destinationHostCount)
        throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);

      List<ContainerTemplateService.State> documentsOnSource = createDocuments(sourceCluster, DOCUMENT_COUNT);

      for (ContainerTemplateService.State cst : documentsOnSource) {
        ContainerTemplateService.State t = TestHelper.createContainerTemplateService(destinationCluster, cst);
        System.out.print(t.documentSelfLink);
      }

      CopyStateTaskService.State finalState = sourceCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(getDocumentLinks(destinationCluster).size(), is(DOCUMENT_COUNT));
    }

    @Test(dataProvider = "hostCounts")
    public void failWhenDestinationUnreachable(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);
      destinationCluster.stop();
      destinationCluster = null;

      createDocuments(sourceCluster, DOCUMENT_COUNT);

      CopyStateTaskService.State finalState = sourceCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "hostCounts")
    public void failWhenSourceUnreachable(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      startClusters(sourceHostCount, destinationHostCount);
      sourceCluster.stop();
      sourceCluster = null;

      CopyStateTaskService.State finalState = destinationCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "hostCounts")
    public void testRenamedFieldHandling(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      sourceCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(sourceHostCount);
      destinationCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.
          create(destinationHostCount);
      sourceCluster = new TestEnvironment.Builder().hostCount(sourceHostCount)
          .cloudServerSet(sourceCloudStore.getServerSet()).build();
      destinationCluster = new TestEnvironment.Builder().hostCount(destinationHostCount)
          .cloudServerSet(sourceCloudStore.getServerSet()).build();
      copyStateTaskServiceState.sourceServers = new HashSet<>();
      for (ServiceHost h : sourceCloudStore.getHosts()) {
        copyStateTaskServiceState.sourceServers.add(new Pair<>(h.getPreferredAddress(), h.getPort()));
      }
      copyStateTaskServiceState.destinationPort = destinationCloudStore.getHosts()[0].getPort();
      copyStateTaskServiceState.sourceFactoryLink = HostServiceFactory.SELF_LINK;
      copyStateTaskServiceState.factoryLink = SampleServiceFactory.SELF_LINK;
      copyStateTaskServiceState.destinationServiceClassName = SampleService.class.getCanonicalName();
      copyStateTaskServiceState.performHostTransformation = Boolean.TRUE;

      Class<?>[] sampleClasses = new Class[]{SampleServiceFactory.class};
      for (int i = 0; i < destinationHostCount; i++) {
        ServiceHostUtils.startServices(destinationCloudStore.getHosts()[i], sampleClasses);
      }
      HostService.State hostState = new HostService.State();
      hostState.hostAddress = "address1";
      hostState.usageTags = Collections.singleton(UsageTag.CLOUD.name());
      hostState.state = HostState.READY;
      hostState.userName = "u1";
      hostState.password = "pwd";
      hostState = TestHelper.createHostService(sourceCloudStore, hostState);

      CopyStateTaskService.State finalState = destinationCluster.callServiceAndWaitForState(
          CopyStateTaskFactoryService.SELF_LINK,
          copyStateTaskServiceState,
          CopyStateTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      String serviceUri = SampleServiceFactory.SELF_LINK + "/" + ServiceUtils.getIDFromDocumentSelfLink(hostState
          .documentSelfLink);
      SampleService.State sample1 = destinationCloudStore.getServiceState(serviceUri, SampleService.State.class);
      assertThat(sample1 == null, is(false));
      assertThat(sample1.field1.equals(hostState.hostAddress), is(true));
      assertThat(sample1.fieldTags.equals(hostState.usageTags), is(true));
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1, 1},
      };
    }

    private void startClusters(Integer sourceHostCount, Integer destinationHostCount) throws Throwable {
      sourceCluster = new TestEnvironment.Builder().hostCount(sourceHostCount).build();
      destinationCluster = new TestEnvironment.Builder().hostCount(destinationHostCount).build();
      copyStateTaskServiceState.destinationPort = destinationCluster.getHosts()[0].getPort();
      copyStateTaskServiceState.sourceServers = new HashSet<>();
      for (ServiceHost h : sourceCluster.getHosts()) {
        copyStateTaskServiceState.sourceServers.add(new Pair<>(h.getPreferredAddress(), h.getPort()));
      }
    }

    private Set<String> getDocumentLinks(TestEnvironment cluster) throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = kindClause;
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      return QueryTaskUtils.getBroadcastQueryDocumentLinks(cluster.sendBroadcastQueryAndWait(queryTask));
    }

    private List<ContainerTemplateService.State> createDocuments(TestEnvironment cluster, int documentCount)
        throws Throwable {
      List<ContainerTemplateService.State> result = new ArrayList<>();
      for (; documentCount > 0; --documentCount) {
        ContainerTemplateService.State containerTemplateState =
            ReflectionUtils.buildValidStartState(ContainerTemplateService.State.class);
        containerTemplateState.name = ContainersConfig.ContainerType.LoadBalancer.name();
        containerTemplateState.cpuCount = 1;
        containerTemplateState.memoryMb = 1024L;
        containerTemplateState.diskGb = 1;
        ContainerTemplateService.State cts = TestHelper.createContainerTemplateService(cluster, containerTemplateState);
        result.add(cts);
      }
      return result;
    }
  }
}
