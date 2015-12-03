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
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link ProvisionNetworkTaskService} class.
 */
public class ProvisionNetworkTaskServiceTest {

  private static ProvisionNetworkTaskService.ProvisionNetworkTaskState buildValidStartState(
      NetworkInstanceRequest.InstanceRequestType requestType) {
    ProvisionNetworkTaskService.ProvisionNetworkTaskState startState =
        new ProvisionNetworkTaskService.ProvisionNetworkTaskState();

    startState.requestType = requestType;
    startState.authCredentialsLink = "foo1";
    startState.resourcePoolLink = "foo2";
    startState.networkDescriptionLink = "foo3";
    startState.isMockRequest = true;
    return startState;
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

    private ProvisionNetworkTaskService provisionNetworkTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionNetworkTaskService = new ProvisionNetworkTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(provisionNetworkTaskService.getOptions(), is(expected));
      assertThat(provisionNetworkTaskService.getProcessingStage(), is(Service.ProcessingStage.CREATED));
    }
  }

  /**
   * This class implements tests for the {@link ProvisionNetworkTaskService#handleStart} method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ProvisionNetworkTaskServiceTest.getFactoryServices();
    }
  }

  /**
   * This class implements tests for the {@link ProvisionNetworkTaskService#handlePatch} method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ProvisionNetworkTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testCreateNetworkSuccess() throws Throwable {

      ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
          NetworkInstanceRequest.InstanceRequestType.CREATE);

      startState.networkServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
      startState.firewallServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);

      ProvisionNetworkTaskService.ProvisionNetworkTaskState returnState = host.postServiceSynchronously(
          ProvisionNetworkTaskFactoryService.SELF_LINK,
          startState,
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class);

      ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = host.waitForServiceState(
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testDeleteNetworkSuccess() throws Throwable {
      ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
          NetworkInstanceRequest.InstanceRequestType.DELETE);

      startState.networkServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
      startState.firewallServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
      startState.isMockRequest = true;

      ProvisionNetworkTaskService.ProvisionNetworkTaskState returnState = host.postServiceSynchronously(
          ProvisionNetworkTaskFactoryService.SELF_LINK,
          startState,
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class);

      ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = host.waitForServiceState(
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testCreateNetworkServiceAdapterFailure() throws Throwable {
      ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
          NetworkInstanceRequest.InstanceRequestType.CREATE);

      startState.networkServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceFailureAdapter.SELF_LINK);
      startState.firewallServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
      startState.isMockRequest = true;

      ProvisionNetworkTaskService.ProvisionNetworkTaskState returnState = host.postServiceSynchronously(
          ProvisionNetworkTaskFactoryService.SELF_LINK,
          startState,
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class);

      ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = host.waitForServiceState(
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testCreateFirewallServiceAdapterFailure() throws Throwable {
      ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
          NetworkInstanceRequest.InstanceRequestType.CREATE);

      startState.networkServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
      startState.firewallServiceReference = UriUtils.buildUri(
          host,
          MockAdapter.MockNetworkInstanceFailureAdapter.SELF_LINK);
      startState.isMockRequest = true;

      ProvisionNetworkTaskService.ProvisionNetworkTaskState returnState = host.postServiceSynchronously(
          ProvisionNetworkTaskFactoryService.SELF_LINK,
          startState,
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class);

      ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = host.waitForServiceState(
          ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }


}
