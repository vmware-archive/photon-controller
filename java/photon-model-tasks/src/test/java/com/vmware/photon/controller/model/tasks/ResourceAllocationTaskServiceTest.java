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

import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
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
  private static final String RESOURCE_POOL = "http://resourcePool";

  private static ComputeService.ComputeState createParentCompute(
      TestHost host,
      String tenant,
      String zoneId,
      String resourcePool) throws Throwable {
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
    if (tenant != null) {
      cd.tenantLinks = new ArrayList<>();
      cd.tenantLinks.add(tenant);
    }
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
    if (tenant != null) {
      cs.tenantLinks = new ArrayList<>();
      cs.tenantLinks.add(tenant);
    }
    ComputeService.ComputeState cs1 = host.postServiceSynchronously(
        ComputeFactoryService.SELF_LINK,
        cs,
        ComputeService.ComputeState.class);

    return cs1;
  }

  private static ComputeService.ComputeState createParentCompute(
      TestHost host,
      String tenant) throws Throwable {
    return createParentCompute(host, tenant, ZONE_ID, RESOURCE_POOL);
  }

  private static ComputeDescriptionService.ComputeDescription createComputeDescription(
      TestHost host,
      String tenant,
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
    if (tenant != null) {
      cd.tenantLinks = new ArrayList<>();
      cd.tenantLinks.add(tenant);
    }
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

  private static List<String> createDiskDescription(TestHost host, String tenant) throws Throwable {
    DiskService.Disk d = new DiskService.Disk();
    d.id = UUID.randomUUID().toString();
    d.type = DiskService.DiskType.HDD;
    d.name = "friendly-name";
    d.capacityMBytes = 100L;
    if (tenant != null) {
      d.tenantLinks = new ArrayList<>();
      d.tenantLinks.add(tenant);
    }
    DiskService.Disk d1 = host.postServiceSynchronously(
        DiskFactoryService.SELF_LINK,
        d,
        DiskService.Disk.class);
    List<String> links = new ArrayList<>();
    links.add(d1.documentSelfLink);
    return links;
  }

  private static List<String> createNetworkDescription(
      TestHost host, String tenant) throws Throwable {
    NetworkInterfaceService.NetworkInterfaceState n = new NetworkInterfaceService.NetworkInterfaceState();
    n.id = UUID.randomUUID().toString();
    if (tenant != null) {
      n.tenantLinks = new ArrayList<>();
      n.tenantLinks.add(tenant);
    }
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
      String tenant,
      ComputeDescriptionService.ComputeDescription cd,
      List<String> diskDescriptionLinks,
      List<String> networkDescriptionLinks) throws Throwable {
    ResourceDescriptionService.ResourceDescription rd = new ResourceDescriptionService.ResourceDescription();
    rd.computeType = ComputeType.VM_GUEST.toString();
    rd.computeDescriptionLink = cd.documentSelfLink;
    if (tenant != null) {
      rd.tenantLinks = new ArrayList<>();
      rd.tenantLinks.add(tenant);
    }
    rd.diskDescriptionLinks = diskDescriptionLinks;
    rd.networkDescriptionLinks = networkDescriptionLinks;

    return host.postServiceSynchronously(
        ResourceDescriptionFactoryService.SELF_LINK,
        rd,
        ResourceDescriptionService.ResourceDescription.class);
  }

  private static ResourceAllocationTaskState createAllocationRequestWithResourceDescription(
      String tenant,
      ComputeDescriptionService.ComputeDescription cd,
      ResourceDescriptionService.ResourceDescription rd) {
    ResourceAllocationTaskState state = new ResourceAllocationTaskState();
    state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
    state.resourceCount = 2;
    state.resourcePoolLink = "http://resourcePool";
    if (tenant != null) {
      state.tenantLinks = new ArrayList<>();
      state.tenantLinks.add(tenant);
    }
    state.resourceDescriptionLink = rd.documentSelfLink;

    return state;
  }

  private static ResourceAllocationTaskState createAllocationRequest(
      String tenant,
      String computeDescriptionLink,
      List<String> diskDescriptionLinks,
      List<String> networkDescriptionLinks) {
    ResourceAllocationTaskState state = new ResourceAllocationTaskState();
    state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
    state.resourceCount = 2;
    state.resourcePoolLink = "http://resourcePool";
    state.computeDescriptionLink = computeDescriptionLink;
    state.computeType = ComputeType.VM_GUEST.toString();
    state.customProperties = new HashMap<>();
    state.customProperties.put("testProp", "testValue");
    if (tenant != null) {
      state.tenantLinks = new ArrayList<>();
      state.tenantLinks.add(tenant);
    }

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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          createDiskDescription(host, tenant),
          createNetworkDescription(host, tenant));

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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceDescriptionService.ResourceDescription rd = createResourceDescription(host, tenant, cd,
          createDiskDescription(host, tenant),
          createNetworkDescription(host, tenant));

      ResourceAllocationTaskState startState = createAllocationRequestWithResourceDescription(tenant, cd, rd);

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
      String tenant = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState = createAllocationRequest(tenant, null, null, null);

      host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testInvalidResourceCount() throws Throwable {
      String tenant = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState = createAllocationRequest(tenant, "http://computeDescription", null, null);
      startState.resourceCount = -1;

      host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testInvalidErrorThreshold() throws Throwable {
      String tenant = UUID.randomUUID().toString();
      ResourceAllocationTaskState startState = createAllocationRequest(tenant, "http://computeDescription", null, null);
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
      String tenant = UUID.randomUUID().toString();

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink, null, null);

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
            is("No compute resources available with poolId:http://resourcePool"));
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
      String tenant = UUID.randomUUID().toString();

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink, null, null);
      ResourceAllocationTaskState returnState = host.postServiceSynchronously(
          ResourceAllocationTaskFactoryService.SELF_LINK,
          startState,
          ResourceAllocationTaskState.class);

      Thread.sleep(1500);
      createParentCompute(host, tenant);

      ResourceAllocationTaskState completeState = host.waitForServiceState(
          ResourceAllocationTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test (enabled = false)
    public void testWrongTenant() throws Throwable {
      String tenant = "tenant1";

      createParentCompute(host, tenant);

      tenant = "tenant2";

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink, null, null);

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
            is("No compute resources available with poolId:http://resourcePool"));
      } finally {
        host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
      }
    }

    @Test (enabled = false)
    public void testWrongZone() throws Throwable {
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant, "other-zone", RESOURCE_POOL);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink, null, null);

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
            is("No compute resources available with poolId:http://resourcePool"));
      } finally {
        host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
      }
    }

    @Test (enabled = false)
    public void testWrongResourcePool() throws Throwable {
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant, ZONE_ID, "other-resource-pool");

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink, null, null);

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
            is("No compute resources available with poolId:http://resourcePool"));
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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          null,
          createNetworkDescription(host, tenant));

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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          createDiskDescription(host, tenant),
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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      List<String> diskLinks = createDiskDescription(host, tenant);
      diskLinks.add("http://bad-disk-link");

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          diskLinks,
          createNetworkDescription(host, tenant));

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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      List<String> networkLinks = createNetworkDescription(host, tenant);
      networkLinks.add("http://bad-network-link");

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          createDiskDescription(host, tenant),
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
      String tenant = UUID.randomUUID().toString();

      createParentCompute(host, tenant);

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, tenant,
          MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
          MockAdapter.MockFailureBootAdapter.SELF_LINK);

      ResourceAllocationTaskState startState = createAllocationRequest(tenant, cd.documentSelfLink,
          createDiskDescription(host, tenant),
          createNetworkDescription(host, tenant));

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
