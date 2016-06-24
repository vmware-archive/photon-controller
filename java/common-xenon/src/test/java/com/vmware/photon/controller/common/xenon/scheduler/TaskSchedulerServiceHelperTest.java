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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStageFactory;
import com.vmware.photon.controller.common.xenon.helpers.xenon.BasicHostEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.TimeUnit;

/**
 * Tests {@link com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceHelper}.
 */
public class TaskSchedulerServiceHelperTest {

  private BasicServiceHost host;
  private TaskSchedulerService service;
  private String selfLink = TaskSchedulerServiceFactory.SELF_LINK + "/test-service-scheduler";
  private BasicHostEnvironment environment;

  //Set the maintenance interval so it won't be triggered by
  private long testInterval = TimeUnit.SECONDS.toMicros(1000);
  private int tasksLimit = 5;

  private TaskSchedulerService.State buildValidStartupState() {
    TestServiceWithStage.State nestedState = new TestServiceWithStage.State();
    nestedState.taskInfo = new TaskState();
    nestedState.taskInfo.stage = TaskState.TaskStage.STARTED;

    TaskSchedulerService.State state = new TaskSchedulerService.State();
    state.schedulerServiceClassName = TestServiceWithStage.class.getTypeName();
    state.tasksLimits = tasksLimit;
    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }


  @AfterMethod
  public void tearDown() throws Throwable {
    if (environment != null) {
      environment.stop();
      environment = null;
    }
    service = null;
  }

  @DataProvider(name = "hostCount")
  public Object[][] getHostCount() {
    return new Object[][]{
        {1},
        {BasicHostEnvironment.DEFAULT_MULTI_HOST_COUNT}
    };
  }

  @Test(dataProvider = "hostCount")
  public void testSendPatchToOwner(int count) throws Throwable {
    initEnvironment(count);
    startDummyServices(3, 0);
    TaskSchedulerServiceHelper.sendPatchToOwner(service, host, selfLink, new TaskSchedulerService.State(),
        UriUtils.buildUri(host, selfLink));

    // check that dummy services are all in STARTED stage
    QueryTask query = QueryTask.create(
        QueryTaskUtils.buildTaskStatusQuerySpec(
            TestServiceWithStage.State.class,
            TaskState.TaskStage.STARTED))
        .setDirect(true);

    QueryTask response = environment.waitForQuery(query,
        (QueryTask queryTask) ->
            queryTask.results.documentLinks.size() >= 3);
    assertThat(response.results.documentLinks.size(), is(3));
  }

  private void initEnvironment(int count) throws Throwable {
    BasicServiceHost[] hosts = new BasicServiceHost[count];
    for (int i = 0; i < count; i++) {
      hosts[i] = BasicServiceHost.create();
    }
    host = hosts[0];

    environment = new BasicHostEnvironment.Builder().hostList(hosts).build();

    environment.startFactoryServiceSynchronously(TestServiceWithStageFactory.class,
        TestServiceWithStageFactory.SELF_LINK);
    for (int i = 0; i < hosts.length; i++) {
      environment.waitForHostReady(hosts[i]);
    }

    for (BasicServiceHost host : hosts) {
      service = new TaskSchedulerService();
      service.setMaintenanceIntervalMicros(testInterval);
      host.startServiceSynchronously(service, buildValidStartupState(), selfLink, false);
    }
  }

  private void startDummyServices(int countInCreated, int countInStarted) throws Throwable {
    startDummyServices(countInStarted, TaskState.TaskStage.STARTED);
    startDummyServices(countInCreated, TaskState.TaskStage.CREATED);
  }

  private void startDummyServices(int countInCreated, TaskState.TaskStage stage)
      throws Throwable {
    TestServiceWithStage.State startState = new TestServiceWithStage.State();
    startState.taskInfo = new TaskState();
    startState.taskInfo.stage = stage;
    for (int i = 0; i < countInCreated; i++) {
      Operation resultOp = environment.sendPostAndWaitForReplication(TestServiceWithStageFactory.SELF_LINK, startState);
      assertThat(resultOp.getStatusCode(), is(200));
    }
  }
}
