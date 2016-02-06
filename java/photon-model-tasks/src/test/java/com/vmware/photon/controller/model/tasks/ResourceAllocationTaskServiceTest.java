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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.TaskServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskFactoryService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceFactoryService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService.ResourceAllocationTaskState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link ResourceAllocationTaskService} class.
 */
public class ResourceAllocationTaskServiceTest {

  private static final String ZONE_ID = "provider-specific-zone";

  private static ComputeService.ComputeState createParentCompute(
      TestHost host,
      String resourcePool,
      String zoneId) throws Throwable {
    // Create parent ComputeDescription
    ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();
    cd.bootAdapterReference = new URI("http://bootAdapterReference");
    cd.powerAdapterReference = new URI("http://powerAdapterReference");
    cd.instanceAdapterReference = new URI("http://instanceAdapterReference");
    cd.healthAdapterReference = null;
    cd.enumerationAdapterReference = new URI("http://enumerationAdapterReference");
    cd.supportedChildren = new ArrayList<>();
    cd.supportedChildren.add(ComputeType.VM_GUEST.toString());
    cd.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
    cd.costPerMinute = 1;
    cd.cpuMhzPerCore = 1000;
    cd.cpuCount = 2;
    cd.gpuCount = 1;
    cd.currencyUnit = "USD";
    cd.totalMemoryBytes = Integer.MAX_VALUE;
    cd.id = UUID.randomUUID().toString();
    cd.name = "friendly-name";
    cd.regionId = "provider-specific-regions";
    cd.zoneId = zoneId;
    ComputeDescriptionService.ComputeDescription cd1 = host.postServiceSynchronously(
        ComputeDescriptionFactoryService.SELF_LINK,
        cd,
        ComputeDescriptionService.ComputeDescription.class);

    // Create parent Compute
    ComputeService.ComputeState cs = new ComputeService.ComputeState();
    cs.id = UUID.randomUUID().toString();
    cs.descriptionLink = cd1.documentSelfLink;
    cs.resourcePoolLink = resourcePool;
    cs.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");
    ComputeService.ComputeState cs1 = host.postServiceSynchronously(
        ComputeFactoryService.SELF_LINK,
        cs,
        ComputeService.ComputeState.class);

    return cs1;
  }

  private static ComputeService.ComputeState createParentCompute(
      TestHost host,
      String resourcePool) throws Throwable {
    return createParentCompute(host, resourcePool, ZONE_ID);
  }

  private static ComputeDescriptionService.ComputeDescription createComputeDescription(
      TestHost host,
      String instanceAdapterLink,
      String bootAdapterLink) throws Throwable {
    // Create ComputeDescription
    ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();
    cd.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
    cd.costPerMinute = 1;
    cd.cpuMhzPerCore = 1000;
    cd.cpuCount = 2;
    cd.gpuCount = 1;
    cd.currencyUnit = "USD";
    cd.totalMemoryBytes = Integer.MAX_VALUE;
    cd.id = UUID.randomUUID().toString();
    cd.name = "friendly-name";
    cd.regionId = "provider-specific-regions";
    cd.zoneId = "provider-specific-zone";
    // disable periodic maintenance for tests by default.
    cd.healthAdapterReference = null;
    if (instanceAdapterLink != null) {
      cd.instanceAdapterReference = UriUtils.buildUri(host, instanceAdapterLink);
      cd.powerAdapterReference = URI.create("http://powerAdapter");
    }
    if (bootAdapterLink != null) {
      cd.bootAdapterReference = UriUtils.buildUri(host, bootAdapterLink);
    }
    return host.postServiceSynchronously(
        ComputeDescriptionFactoryService.SELF_LINK,
        cd,
        ComputeDescriptionService.ComputeDescription.class);
  }

  private static List<String> createDiskDescription(TestHost host) throws Throwable {
    DiskService.DiskState d = new DiskService.DiskState();
    d.id = UUID.randomUUID().toString();
    d.type = DiskService.DiskType.HDD;
    d.name = "friendly-name";
    d.capacityMBytes = 100L;
    DiskService.DiskState d1 = host.postServiceSynchronously(
        DiskFactoryService.SELF_LINK,
        d,
        DiskService.DiskState.class);
    List<String> links = new ArrayList<>();
    links.add(d1.documentSelfLink);
    return links;
  }

  private static List<String> createNetworkDescription(
      TestHost host) throws Throwable {
    NetworkInterfaceService.NetworkInterfaceState n = new NetworkInterfaceService.NetworkInterfaceState();
    n.id = UUID.randomUUID().toString();
    n.networkDescriptionLink = "http://network-description";
    NetworkInterfaceService.NetworkInterfaceState n1 = host.postServiceSynchronously(
        NetworkInterfaceFactoryService.SELF_LINK,
        n,
        NetworkInterfaceService.NetworkInterfaceState.class);
    List<String> links = new ArrayList<>();
    links.add(n1.documentSelfLink);

    return links;
  }

  private static ResourceDescriptionService.ResourceDescription createResourceDescription(
      TestHost host,
      ComputeDescriptionService.ComputeDescription cd,
      List<String> diskDescriptionLinks,
      List<String> networkDescriptionLinks) throws Throwable {
    ResourceDescriptionService.ResourceDescription rd = new ResourceDescriptionService.ResourceDescription();
    rd.computeType = ComputeType.VM_GUEST.toString();
    rd.computeDescriptionLink = cd.documentSelfLink;
    rd.diskDescriptionLinks = diskDescriptionLinks;
    rd.networkDescriptionLinks = networkDescriptionLinks;

    return host.postServiceSynchronously(
        ResourceDescriptionFactoryService.SELF_LINK,
        rd,
        ResourceDescriptionService.ResourceDescription.class);
  }

  private static ResourceAllocationTaskState createAllocationRequestWithResourceDescription(
      String resourcePool,
      ComputeDescriptionService.ComputeDescription cd,
      ResourceDescriptionService.ResourceDescription rd) {
    ResourceAllocationTaskState state = new ResourceAllocationTaskState();
    state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
    state.resourceCount = 2;
    state.resourcePoolLink = resourcePool;
    state.resourceDescriptionLink = rd.documentSelfLink;

    return state;
  }

  private static ResourceAllocationTaskState createAllocationRequest(
      String resourcePool,
      String computeDescriptionLink,
      List<String> diskDescriptionLinks,
      List<String> networkDescriptionLinks) {
    ResourceAllocationTaskState state = new ResourceAllocationTaskState();
    state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
    state.resourceCount = 2;
    state.resourcePoolLink = resourcePool;
    state.computeDescriptionLink = computeDescriptionLink;
    state.computeType = ComputeType.VM_GUEST.toString();
    state.customProperties = new HashMap<>();
    state.customProperties.put("testProp", "testValue");

    // For most tests, we override resourceDescription.
    state.resourceDescriptionLink = null;

    state.diskDescriptionLinks = diskDescriptionLinks;
    state.networkDescriptionLinks = networkDescriptionLinks;

    return state;
  }

  private static Class[] getFactoryServices() {
    List<Class> services = new ArrayList<>();
    Collections.addAll(services, ModelServices.FACTORIES);
    Collections.addAll(services, TaskServices.FACTORIES);
    Collections.addAll(services, MockAdapter.FACTORIES);
    return services.toArray(new Class[services.size()]);
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ResourceAllocationTaskService provisionComputeTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionComputeTaskService = new ResourceAllocationTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);

      assertThat(provisionComputeTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ResourceAllocationTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testSuccess() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          createDiskDescription(host),
          createNetworkDescription(host));

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    /**
     * Call task with a template resource description (ResourceAllocationTaskState.resourceDescriptionLink).
     */
    @Test
    public void testSuccessWithResourceDescription() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceDescriptionService.ResourceDescription rd = createResourceDescription(host, cd,
          createDiskDescription(host),
          createNetworkDescription(host));

      ResourceAllocationTaskState startState = createAllocationRequestWithResourceDescription(resourcePool, cd, rd);

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testMissingComputeDescription() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, null, null, null);

      host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testInvalidResourceCount() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState =
          createAllocationRequest(resourcePool, "http://computeDescription", null, null);
      startState.resourceCount = -1;

      host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testInvalidErrorThreshold() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState =
          createAllocationRequest(resourcePool, "http://computeDescription", null, null);
      startState.errorThreshold = -1;

      host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class,
          IllegalArgumentException.class);
    }
  }

  /**
   * This class implements tests for QUERYING_AVAILABLE_COMPUTE_RESOURCES substage.
   */
  public class QueryAvailableComputeResourcesTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ResourceAllocationTaskServiceTest.getFactoryServices();
    }

    /**
     * Do not create parent compute, and resource allocation request will fail.
     */
    @Test
    public void testNoResourcesFound() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink, null, null);

      try {
        // Lower timeout to 5 seconds
        host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(5));

        ResourceAllocationTaskState returnState = host.postServiceSynchronously(
            ResourceAllocationTaskFactoryService.SELF_LINK,
            startState,
            ResourceAllocationTaskState.class);

        ResourceAllocationTaskState completeState = host.waitForServiceState(
            ResourceAllocationTaskState.class,
            returnState.documentSelfLink,
            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
        );

        assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
        assertThat(completeState.taskInfo.failure.message,
            is("No compute resources available with poolId:" + resourcePool));
      } finally {
        host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
      }
    }

    /**
     * Create parent compute shortly after the ResourceAllocationTask is called, the task should be able
     * to retry and complete successfully.
     */
    @Test
    public void testParentResourceBecomeAvailable() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink, null, null);
      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      Thread.sleep(1500);
      createParentCompute(host, resourcePool);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testWrongResourcePool() throws Throwable {
      String resourcePool1 = UUID.randomUUID().toString();
      String resourcePool2 = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool1, ZONE_ID);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool2, cd.documentSelfLink, null, null);

      try {
        // Lower timeout to 5 seconds
        host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(5));

        ResourceAllocationTaskState returnState = host.postServiceSynchronously(
            ResourceAllocationTaskFactoryService.SELF_LINK,
            startState,
            ResourceAllocationTaskState.class);

        ResourceAllocationTaskState completeState = host.waitForServiceState(
            ResourceAllocationTaskState.class,
            returnState.documentSelfLink,
            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
        );

        assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
        assertThat(completeState.taskInfo.failure.message,
            is("No compute resources available with poolId:" + resourcePool2));
      } finally {
        host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
      }
    }

    @Test
    public void testWrongZone() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool, "other-zone");

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink, null, null);

      try {
        // Lower timeout to 5 seconds
        host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(5));

        ResourceAllocationTaskState returnState = host.postServiceSynchronously(
            ResourceAllocationTaskFactoryService.SELF_LINK,
            startState,
            ResourceAllocationTaskState.class);

        ResourceAllocationTaskState completeState = host.waitForServiceState(
            ResourceAllocationTaskState.class,
            returnState.documentSelfLink,
            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
        );

        assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
        assertThat(completeState.taskInfo.failure.message,
            is("No compute resources available with poolId:" + resourcePool));
      } finally {
        host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
      }
    }
  }

  /**
   * This class implements tests for PROVISIONING_VM_GUESTS / PROVISIONING_CONTAINERS substage.
   */
  public class ComputeResourceProvisioningTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ResourceAllocationTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testNoDisk() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          null,
          createNetworkDescription(host));

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testNoNetwork() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          createDiskDescription(host),
          null);

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testNoDiskOrNetwork() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          null,
          null);

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testProvisionDiskFailure() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      List<String> diskLinks = createDiskDescription(host);
      diskLinks.add("http://bad-disk-link");

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          diskLinks,
          createNetworkDescription(host));

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testProvisionNetworkFailure() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      List<String> networkLinks = createNetworkDescription(host);
      networkLinks.add("http://bad-network-link");

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          createDiskDescription(host),
          networkLinks);

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testProvisionCompulteFailure() throws Throwable {
      String resourcePool = UUID.randomUUID().toString();

      createParentCompute(host, resourcePool);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host,
          MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
          MockAdapter.MockFailureBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(resourcePool, cd.documentSelfLink,
          createDiskDescription(host),
          createNetworkDescription(host));

      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }

}
