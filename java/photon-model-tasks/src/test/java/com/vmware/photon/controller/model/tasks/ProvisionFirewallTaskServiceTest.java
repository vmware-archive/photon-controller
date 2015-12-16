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
import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link ProvisionFirewallTaskService} class.
 */
public class ProvisionFirewallTaskServiceTest {

  private static ProvisionFirewallTaskService.ProvisionFirewallTaskState buildValidStartState(
      TestHost host,
      FirewallInstanceRequest.InstanceRequestType requestType) {
    ProvisionFirewallTaskService.ProvisionFirewallTaskState startState =
        new ProvisionFirewallTaskService.ProvisionFirewallTaskState();

    startState.requestType = requestType;
    startState.authCredentialsLink = "http//authCredentialsLink";
    startState.firewallDescriptionLink = "http://firewallDescriptionLink";
    startState.resourcePoolLink = "http://resourcePoolLink";
    startState.regionID = "region-id";
    startState.firewallServiceReference = UriUtils.buildUri(
        host,
        MockAdapter.MockFirewallInstanceSuccessAdapter.SELF_LINK);
    startState.isMockRequest = true;

    return startState;
  }

  private static ProvisionFirewallTaskService.ProvisionFirewallTaskState postAndWaitForService(
      TestHost host,
      ProvisionFirewallTaskService.ProvisionFirewallTaskState startState) throws Throwable {
    ProvisionFirewallTaskService.ProvisionFirewallTaskState returnState = host.postServiceSynchronously(
        ProvisionFirewallTaskFactoryService.SELF_LINK,
        startState,
        ProvisionFirewallTaskService.ProvisionFirewallTaskState.class);

    ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = host.waitForServiceState(
        ProvisionFirewallTaskService.ProvisionFirewallTaskState.class,
        returnState.documentSelfLink,
        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

    return completeState;
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
    private ProvisionFirewallTaskService provisionFirewallTaskService;

    @BeforeMethod
    public void setupTest() {
      provisionFirewallTaskService = new ProvisionFirewallTaskService();
    }

    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(provisionFirewallTaskService.getOptions(), is(expected));
      assertThat(provisionFirewallTaskService.getProcessingStage(), is(Service.ProcessingStage.CREATED));
    }
  }

  /**
   * This class implements tests for the {@link ProvisionFirewallTaskService#handleStart} method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ProvisionFirewallTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testValidateProvisionFirewallTaskService() throws Throwable {
      ProvisionFirewallTaskService.ProvisionFirewallTaskState startState =
          buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE);
      ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = postAndWaitForService(host, startState);
      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @DataProvider(name = "createMissingValues")
    public Object[][] createMissingValues() {
      ProvisionFirewallTaskService.ProvisionFirewallTaskState
          invalidRequestType =
          buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE),
          invalidAuthCredentialsLink =
              buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE),
          invalidResourcePoolLink =
              buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE),
          invalidFirewallDescriptionLink =
              buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE),
          invalidFirewallServiceReference =
              buildValidStartState(host, FirewallInstanceRequest.InstanceRequestType.CREATE);

      invalidRequestType.requestType = null;
      invalidAuthCredentialsLink.authCredentialsLink = null;
      invalidResourcePoolLink.resourcePoolLink = null;
      invalidFirewallDescriptionLink.firewallDescriptionLink = null;
      invalidFirewallServiceReference.firewallServiceReference = null;

      return new Object[][]{
          {invalidRequestType},
          {invalidAuthCredentialsLink},
          {invalidResourcePoolLink},
          {invalidFirewallDescriptionLink},
          {invalidFirewallServiceReference},
      };
    }

    @Test(dataProvider = "createMissingValues")
    public void testMissingValue(ProvisionFirewallTaskService.ProvisionFirewallTaskState startState) throws Throwable {
      ProvisionFirewallTaskService.ProvisionFirewallTaskState returnState = host.postServiceSynchronously(
          ProvisionFirewallTaskFactoryService.SELF_LINK,
          startState,
          ProvisionFirewallTaskService.ProvisionFirewallTaskState.class, IllegalArgumentException.class);
    }
  }


  /**
   * This class implements tests for the {@link ProvisionFirewallTaskService#handlePatch} method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ProvisionFirewallTaskServiceTest.getFactoryServices();
    }
    //TODO jaked
  }
}
