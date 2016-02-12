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
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link ResourceRemovalTaskService} class.
 */
public class ResourceRemovalTaskServiceTest {

  private ResourceRemovalTaskService.ResourceRemovalTaskState buildValidStartState() {
    ResourceRemovalTaskService.ResourceRemovalTaskState startState =
        new ResourceRemovalTaskService.ResourceRemovalTaskState();

    startState.resourceQuerySpec = new QueryTask.QuerySpecification();
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ComputeService.ComputeState.class));
    startState.resourceQuerySpec.query.addBooleanClause(kindClause);
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

    private ResourceRemovalTaskService resourceRemovalTaskService;

    @BeforeMethod
    public void setUpTest() {
      resourceRemovalTaskService = new ResourceRemovalTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(resourceRemovalTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ResourceRemovalTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testMissingResourceQuerySpec() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
      startState.resourceQuerySpec = null;

      host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingTaskInfo() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
      startState.taskInfo = null;

      ResourceRemovalTaskService.ResourceRemovalTaskState returnState = host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class);

      assertThat(returnState.taskInfo, notNullValue());
      assertThat(returnState.taskInfo.stage, is(TaskState.TaskStage.CREATED));
    }

    @Test
    public void testMissingTaskSubStage() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
      startState.taskSubStage = null;

      ResourceRemovalTaskService.ResourceRemovalTaskState returnState = host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class);

      assertThat(returnState.taskSubStage, notNullValue());
      assertThat(returnState.taskSubStage, is(ResourceRemovalTaskService.SubStage.WAITING_FOR_QUERY_COMPLETION));
    }
  }

  /**
   * This class implements EndToEnd tests for {@link ResourceRemovalTaskService}.
   */
  public class EndToEndTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ResourceRemovalTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testQueryResourceReturnZeroDocument() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();

      ResourceRemovalTaskService.ResourceRemovalTaskState returnState = host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class);

      returnState = host.waitForServiceState(
          ResourceRemovalTaskService.ResourceRemovalTaskState.class,
          returnState.documentSelfLink,
          state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      assertThat(returnState.taskSubStage, is(ResourceRemovalTaskService.SubStage.FINISHED));
    }

    @Test
    public void testResourceRemovalSuccess() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
      ComputeService.ComputeStateWithDescription cs = ModelUtils.createComputeWithDescription(
          host,
          MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
          null);

      ResourceRemovalTaskService.ResourceRemovalTaskState returnState = host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class);

      returnState = host.waitForServiceState(
          ResourceRemovalTaskService.ResourceRemovalTaskState.class,
          returnState.documentSelfLink,
          state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      assertThat(returnState.taskSubStage, is(ResourceRemovalTaskService.SubStage.FINISHED));

      // Clean up the compute and description documents
      host.deleteServiceSynchronously(cs.documentSelfLink);
      host.deleteServiceSynchronously(cs.descriptionLink);

      // Stop factory service.
      host.deleteServiceSynchronously(ResourceRemovalTaskFactoryService.SELF_LINK);

      // stop the removal task
      host.stopServiceSynchronously(returnState.documentSelfLink);

      // restart and check service restart successfully.
      host.startServiceAndWait(ResourceRemovalTaskFactoryService.class,
              ResourceRemovalTaskFactoryService.SELF_LINK);

      ResourceRemovalTaskService.ResourceRemovalTaskState stateAfterRestart =
      host.getServiceSynchronously(returnState.documentSelfLink,
              ResourceRemovalTaskService.ResourceRemovalTaskState.class);
      assertThat(stateAfterRestart, notNullValue());
    }

    @Test
    public void testResourceRemovalFailure() throws Throwable {
      ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
      ComputeService.ComputeStateWithDescription cs = ModelUtils.createComputeWithDescription(
          host,
          MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
          null);

      ResourceRemovalTaskService.ResourceRemovalTaskState returnState = host.postServiceSynchronously(
          ResourceRemovalTaskFactoryService.SELF_LINK,
          startState,
          ResourceRemovalTaskService.ResourceRemovalTaskState.class);

      host.waitForServiceState(
          ResourceRemovalTaskService.ResourceRemovalTaskState.class,
          returnState.documentSelfLink,
          state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      // Clean up the compute and description documents
      host.deleteServiceSynchronously(cs.documentSelfLink);
      host.deleteServiceSynchronously(cs.descriptionLink);
    }
  }
}
