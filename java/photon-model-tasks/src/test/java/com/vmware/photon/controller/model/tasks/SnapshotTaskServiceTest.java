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
import com.vmware.photon.controller.model.resources.SnapshotFactoryService;
import com.vmware.photon.controller.model.resources.SnapshotService;
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
 * This class implements tests for the {@link SnapshotTaskService} class.
 */
public class SnapshotTaskServiceTest {
  private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
  private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID().toString();

  private static SnapshotTaskService.SnapshotTaskState buildValidStartState(
      SnapshotService.SnapshotState st) throws Throwable {
    SnapshotTaskService.SnapshotTaskState state = new SnapshotTaskService.SnapshotTaskState();
    state.snapshotAdapterReference = new URI("http://snapshotAdapterReference");

    state.isMockRequest = true;
    state.snapshotLink = st.documentSelfLink;

    return state;
  }

  private static SnapshotService.SnapshotState createSnapshotService(TestHost host) throws Throwable {
    SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
    st.id = UUID.randomUUID().toString();
    st.name = "friendly-name";
    st.computeLink = "compute-link";
    st.description = "description";
    st.customProperties = new HashMap<>();
    st.customProperties.put("defaultKey", "defaultVal");

    SnapshotService.SnapshotState serviceState = host.postServiceSynchronously(
        SnapshotFactoryService.SELF_LINK,
        st,
        SnapshotService.SnapshotState.class);

    return serviceState;
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

    private SnapshotTaskService snapshotTaskService;

    @BeforeMethod
    public void setUpTest() {
      snapshotTaskService = new SnapshotTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(snapshotTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {

    @Override
    protected Class[] getFactoryServices() {
      return SnapshotTaskServiceTest.getFactoryServices();
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      super.setUpClass();
    }

    @Test
    public void testMissingSnapshotLink() throws Throwable {
      SnapshotService.SnapshotState serviceState = new SnapshotService.SnapshotState();
      serviceState.computeLink = null;

      SnapshotTaskService.SnapshotTaskState taskState = buildValidStartState(serviceState);

      host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          taskState,
          SnapshotTaskService.SnapshotTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingSnapshotAdapterReference() throws Throwable {
      SnapshotService.SnapshotState serviceState = createSnapshotService(host);

      SnapshotTaskService.SnapshotTaskState taskState = buildValidStartState(serviceState);
      taskState.snapshotAdapterReference = null;

      host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          taskState,
          SnapshotTaskService.SnapshotTaskState.class,
          IllegalArgumentException.class);
    }
  }

  /**
   * This class implements EndToEnd tests for {@link SnapshotTaskService}.
   */

  public class EndToEndTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return SnapshotTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testSuccess() throws Throwable {
      SnapshotService.SnapshotState serviceState = createSnapshotService(host);

      SnapshotTaskService.SnapshotTaskState startState = buildValidStartState(serviceState);
      startState.snapshotAdapterReference = UriUtils.buildUri(host, MockAdapter.MockSnapshotSuccessAdapter.SELF_LINK);

      SnapshotTaskService.SnapshotTaskState returnState = host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          startState,
          SnapshotTaskService.SnapshotTaskState.class);

      returnState = host.waitForServiceState(
          SnapshotTaskService.SnapshotTaskState.class,
          returnState.documentSelfLink,
          state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

      assertThat(returnState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testFailure() throws Throwable {
      SnapshotService.SnapshotState serviceState = createSnapshotService(host);

      SnapshotTaskService.SnapshotTaskState startState = buildValidStartState(serviceState);
      startState.snapshotAdapterReference = UriUtils.buildUri(host, MockAdapter.MockSnapshotFailureAdapter.SELF_LINK);

      SnapshotTaskService.SnapshotTaskState returnState = host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          startState,
          SnapshotTaskService.SnapshotTaskState.class);


      returnState = host.waitForServiceState(
          SnapshotTaskService.SnapshotTaskState.class,
          returnState.documentSelfLink,
          state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

      assertThat(returnState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
