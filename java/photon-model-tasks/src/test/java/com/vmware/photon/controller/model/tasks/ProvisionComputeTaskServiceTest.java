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
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Service;
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

/**
 * This class implements tests for the {@link ProvisionComputeTaskService} class.
 */
public class ProvisionComputeTaskServiceTest {
  private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
  private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID().toString();

  private static ComputeDescriptionService.ComputeDescription createComputeDescription(
      TestHost host,
      String instanceAdapterLink,
      String bootAdapterLink) throws Throwable {
    ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.buildValidStartState();
    // disable periodic maintenance for tests by default.
    cd.healthAdapterReference = null;
    if (instanceAdapterLink != null) {
      cd.instanceAdapterReference = UriUtils.buildUri(host, instanceAdapterLink);
    }
    if (bootAdapterLink != null) {
      cd.bootAdapterReference = UriUtils.buildUri(host, bootAdapterLink);
    }
    return host.postServiceSynchronously(
        ComputeDescriptionFactoryService.SELF_LINK,
        cd,
        ComputeDescriptionService.ComputeDescription.class);
  }

  private static ComputeService.ComputeStateWithDescription createCompute(
      TestHost host,
      ComputeDescriptionService.ComputeDescription cd) throws Throwable {
    ComputeService.ComputeState cs = new ComputeService.ComputeStateWithDescription();
    cs.id = UUID.randomUUID().toString();
    cs.descriptionLink = cd.documentSelfLink;
    cs.resourcePoolLink = null;
    cs.address = "10.0.0.1";
    cs.primaryMAC = "01:23:45:67:89:ab";
    cs.powerState = ComputeService.PowerState.ON;
    cs.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");
    cs.diskLinks = new ArrayList<>();
    cs.diskLinks.add("http://disk");
    cs.networkLinks = new ArrayList<>();
    cs.networkLinks.add("http://network");
    cs.customProperties = new HashMap<>();
    cs.customProperties.put(TEST_DESC_PROPERTY_NAME, TEST_DESC_PROPERTY_VALUE);
    cs.tenantLinks = new ArrayList<>();
    cs.tenantLinks.add("http://tenant");

    ComputeService.ComputeState returnState = host.postServiceSynchronously(
        ComputeFactoryService.SELF_LINK,
        cs,
        ComputeService.ComputeState.class);

    return ComputeService.ComputeStateWithDescription.create(cd, returnState);
  }

  private static Class[] getFactoryServices() {
    List<Class> services = new ArrayList<>();
    Collections.addAll(services, ModelServices.FACTORIES);
    Collections.addAll(services, TaskServices.FACTORIES);
    Collections.addAll(services, MockAdapter.FACTORIES);
    return services.toArray(new Class[services.size()]);
  }

  private static ComputeService.ComputeStateWithDescription createComputeWithDescription(
      TestHost host,
      String instanceAdapterLink,
      String bootAdapterLink) throws Throwable {
    return createCompute(host,
        createComputeDescription(host, instanceAdapterLink, bootAdapterLink));
  }

  private static ComputeService.ComputeStateWithDescription createComputeWithDescription(
      TestHost host,
      ComputeType supportedChildren) throws Throwable {
    return createComputeWithDescription(host, null, null);
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ProvisionComputeTaskService provisionComputeTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionComputeTaskService = new ProvisionComputeTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
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
      return ProvisionComputeTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testValidateComputeHost() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(host, ComputeType.VM_HOST);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = cs.documentSelfLink;
      startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST;
      startState.isMockRequest = true;

      ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class);

      ProvisionComputeTaskService.ProvisionComputeTaskState startedState = host.waitForServiceState(
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(startedState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testMissingComputeLink() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(host, ComputeType.VM_HOST);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = null;

      host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingSubStage() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(host, ComputeType.VM_HOST);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.taskSubStage = null;

      host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingInstanceAdapterReference() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.buildValidStartState();
      cd.healthAdapterReference = null;
      cd.supportedChildren = new ArrayList<>();
      cd.supportedChildren.add(ComputeType.DOCKER_CONTAINER.toString());
      cd.instanceAdapterReference = null;
      ComputeDescriptionService.ComputeDescription cd1 = host.postServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK,
          cd,
          ComputeDescriptionService.ComputeDescription.class);

      ComputeService.ComputeStateWithDescription cs = createCompute(host, cd1);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = cs.documentSelfLink;
      startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;

      ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class);

      ProvisionComputeTaskService.ProvisionComputeTaskState startedState = host.waitForServiceState(
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(startedState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
      assertThat(startedState.taskInfo.failure.message, is("computeHost does not have create service specified"));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ProvisionComputeTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testCreateAndBootHostSuccess() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(
          host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockSuccessBootAdapter.SELF_LINK);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = cs.documentSelfLink;
      startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
      startState.isMockRequest = true;

      ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class);

      ProvisionComputeTaskService.ProvisionComputeTaskState startedState = host.waitForServiceState(
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(startedState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testCreateHostFailure() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(
          host,
          MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
          null);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = cs.documentSelfLink;
      startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
      startState.isMockRequest = true;

      ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class);

      ProvisionComputeTaskService.ProvisionComputeTaskState startedState = host.waitForServiceState(
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(startedState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testBootHostFailure() throws Throwable {
      ComputeService.ComputeStateWithDescription cs = createComputeWithDescription(
          host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          MockAdapter.MockFailureBootAdapter.SELF_LINK);

      ProvisionComputeTaskService.ProvisionComputeTaskState startState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      startState.computeLink = cs.documentSelfLink;
      startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
      startState.isMockRequest = true;

      ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host.postServiceSynchronously(
          ProvisionComputeTaskFactoryService.SELF_LINK,
          startState,
          ProvisionComputeTaskService.ProvisionComputeTaskState.class);

      ProvisionComputeTaskService.ProvisionComputeTaskState startedState = host.waitForServiceState(
          ProvisionComputeTaskService.ProvisionComputeTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(startedState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
