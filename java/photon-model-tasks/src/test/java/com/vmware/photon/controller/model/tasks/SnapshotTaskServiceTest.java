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
import com.vmware.photon.controller.model.resources.SnapshotFactoryService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.xenon.common.Service;


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
    SnapshotTaskService.SnapshotTaskState state =  new SnapshotTaskService.SnapshotTaskState();
    state.snapshotAdapterReference =  new URI("http://snapshotAdapterReference");

    state.isMockRequest = true;
    state.snapshotLink = st.documentSelfLink;


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

    private SnapshotTaskService snapshotTaskService;

    @BeforeMethod
    public void setUpTest() {
      snapshotTaskService = new SnapshotTaskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
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

    private SnapshotService.SnapshotState st;

    @Override
    protected Class[] getFactoryServices() {
      return SnapshotTaskServiceTest.getFactoryServices();
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      super.setUpClass();
      st = new SnapshotService.SnapshotState();
      st.name = "friendly-name";
      st.computeLink = "compute-link";
      st.description = "description";
      st.customProperties = new HashMap<>();
      st.customProperties.put("defaultKey", "defaultVal");
    }

    @Test
    public void testMissingComputeDescription() throws Throwable {
      st.id = UUID.randomUUID().toString();

      SnapshotService.SnapshotState serviceState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          st,
          SnapshotService.SnapshotState.class);

      SnapshotTaskService.SnapshotTaskState  taskState = buildValidStartState(serviceState);
      taskState.snapshotLink = null;

      host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          taskState,
          SnapshotTaskService.SnapshotTaskState.class,
          IllegalArgumentException.class);
    }


    @Test
    public void testMissingSnapshotAdapterReference() throws Throwable {
      st.id = UUID.randomUUID().toString();
      SnapshotService.SnapshotState serviceState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          st,
          SnapshotService.SnapshotState.class);

      SnapshotTaskService.SnapshotTaskState  taskState = buildValidStartState(serviceState);
      taskState.snapshotAdapterReference = null;

      host.postServiceSynchronously(
          SnapshotTaskFactoryService.SELF_LINK,
          taskState,
          SnapshotTaskService.SnapshotTaskState.class,
          IllegalArgumentException.class);
    }
  }
}
