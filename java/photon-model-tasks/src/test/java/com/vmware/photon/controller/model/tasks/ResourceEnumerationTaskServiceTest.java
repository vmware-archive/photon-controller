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
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.BeforeClass;
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
public class ResourceEnumerationTaskServiceTest {
  private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
  private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID().toString();

  private static ResourceEnumerationTaskService.ResourceEnumerationTaskState buildValidStartState(
      ComputeService.ComputeStateWithDescription computeStateWithDescription) throws Throwable {
    ResourceEnumerationTaskService.ResourceEnumerationTaskState state =
        new ResourceEnumerationTaskService.ResourceEnumerationTaskState();
    state.adapterManagementReference = new URI("http://adapterManagementReference");
    state.resourcePoolLink = "http://resourcePoolLink";
    state.enumerationAction = EnumerationAction.RERESH;
    if (computeStateWithDescription != null) {
      state.computeDescriptionLink = computeStateWithDescription.descriptionLink;
      state.parentComputeLink = computeStateWithDescription.documentSelfLink;
    }
    return state;
  }

  private static ComputeDescriptionService.ComputeDescription createComputeDescription(
      TestHost host, String enumerationAdapterReference) throws Throwable {
    ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest.buildValidStartState();
    cd.healthAdapterReference = null;
    cd.enumerationAdapterReference = UriUtils.buildUri(host, enumerationAdapterReference);

    return host.postServiceSynchronously(
        ComputeDescriptionFactoryService.SELF_LINK, cd, ComputeDescriptionService.ComputeDescription.class);
  }

  private static ComputeService.ComputeStateWithDescription createCompute(
      TestHost host, ComputeDescriptionService.ComputeDescription cd) throws Throwable {
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
        ComputeFactoryService.SELF_LINK, cs, ComputeService.ComputeState.class);

    return ComputeService.ComputeStateWithDescription.create(cd, returnState);
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

    private ResourceEnumerationTaskService resourceEnumerationTaskService;

    @BeforeMethod
    public void setUpTest() {
      resourceEnumerationTaskService = new ResourceEnumerationTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(resourceEnumerationTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {

    private ComputeService.ComputeStateWithDescription computeHost;

    @Override
    protected Class[] getFactoryServices() {
      return ResourceEnumerationTaskServiceTest.getFactoryServices();
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      super.setUpClass();

      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(host, "http://enumerationAdapter");
      computeHost = createCompute(host, cd);
    }

    @Test
    public void testMissingComputeDescription() throws Throwable {
      ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(null);
      host.postServiceSynchronously(
          ResourceEnumerationTaskFactoryService.SELF_LINK,
          state,
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingAdapterManagementReference() throws Throwable {
      ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(computeHost);
      state.adapterManagementReference = null;

      host.postServiceSynchronously(
          ResourceEnumerationTaskFactoryService.SELF_LINK,
          state,
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingResourcePoolLink() throws Throwable {
      ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(computeHost);
      state.resourcePoolLink = null;

      host.postServiceSynchronously(
          ResourceEnumerationTaskFactoryService.SELF_LINK,
          state,
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
          IllegalArgumentException.class);
    }
  }

  /**
   * This class implements EndToEnd tests for the ResourceEnumerationTaskService.
   */
  public class EndToEndTest extends BaseModelTest {

    @Override
    protected Class[] getFactoryServices() {
      return ResourceEnumerationTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testSuccess() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
          host, MockAdapter.MockSuccessEnumerationAdapter.SELF_LINK);
      ComputeService.ComputeStateWithDescription computeHost = createCompute(host, cd);

      ResourceEnumerationTaskService.ResourceEnumerationTaskState startState = host.postServiceSynchronously(
          ResourceEnumerationTaskFactoryService.SELF_LINK,
          buildValidStartState(computeHost),
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class);

      ResourceEnumerationTaskService.ResourceEnumerationTaskState newState = host.waitForServiceState(
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
          startState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(newState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }


    @Test
    public void testFailure() throws Throwable {
      ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
          host, MockAdapter.MockFailureEnumerationAdapter.SELF_LINK);
      ComputeService.ComputeStateWithDescription computeHost = createCompute(host, cd);

      ResourceEnumerationTaskService.ResourceEnumerationTaskState startState = host.postServiceSynchronously(
          ResourceEnumerationTaskFactoryService.SELF_LINK,
          buildValidStartState(computeHost),
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class);

      ResourceEnumerationTaskService.ResourceEnumerationTaskState newState = host.waitForServiceState(
          ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
          startState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());

      assertThat(newState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
