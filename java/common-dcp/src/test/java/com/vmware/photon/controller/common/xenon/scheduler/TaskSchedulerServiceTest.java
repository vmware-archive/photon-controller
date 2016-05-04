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
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStageFactory;
import com.vmware.photon.controller.common.xenon.helpers.xenon.BasicHostEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Tests {@link TaskSchedulerService}.
 */
public class TaskSchedulerServiceTest {

  private BasicServiceHost host;
  private TaskSchedulerService service;
  private String selfLink = TaskSchedulerServiceFactory.SELF_LINK + "/test-service-scheduler";

  private long testInterval = TimeUnit.SECONDS.toMicros(1);
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

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new TaskSchedulerService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskSchedulerService();
      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      Operation startOp = host.startServiceSynchronously(service, buildValidStartupState(), selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      TaskSchedulerService.State savedState = host.getServiceState(TaskSchedulerService.State.class, selfLink);
      assertThat(savedState.documentSelfLink, is(selfLink));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskSchedulerService();

      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState(), selfLink);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody("invalid body");

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }
  }

  /**
   * EndToEndTest for TaskSchedulerService.
   */
  public class EndToEndTest {
    BasicHostEnvironment environment;

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
    public void testProcessWithTasksAllInCreatedStage(int count) throws Throwable {
      initEnvironment(count);
      startDummyServices(3, 0);

      // check that dummy services are all in STARTED stage
      QueryTask query = QueryTask.create(
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestServiceWithStage.State.class,
              TaskState.TaskStage.STARTED))
          .setDirect(true);

      QueryTask response = environment.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= 3;
            }
          });
      assertThat(response.results.documentLinks.size(), is(3));
    }

    @Test(dataProvider = "hostCount")
    public void testProcessWithTasksPartiallyInCreatedStage(int count) throws Throwable {
      initEnvironment(count);
      startDummyServices(2, 2);

      // check that dummy services are all in STARTED stage
      QueryTask query = QueryTask.create(
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestServiceWithStage.State.class,
              TaskState.TaskStage.STARTED))
          .setDirect(true);

      QueryTask response = environment.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= 4;
            }
          });
      assertThat(response.results.documentLinks.size(), is(4));
    }

    @Test(dataProvider = "hostCount")
    public void testProcessWithMoreThanThresholdNumberOfTasks(int count) throws Throwable {
      initEnvironment(count);
      startDummyServices(tasksLimit, 3);

      // check that no more than the threshold number of dummy services are all in STARTED stage
      QueryTask query = QueryTask.create(
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestServiceWithStage.State.class,
              TaskState.TaskStage.STARTED))
          .setDirect(true);

      QueryTask response = environment.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= tasksLimit;
            }
          });
      assertThat(response.results.documentLinks.size(), greaterThanOrEqualTo(tasksLimit));
    }

    @Test(dataProvider = "hostCount")
    public void testProcessWithMoreThanThresholdNumberInStarted(int count) throws Throwable {
      initEnvironment(count);
      startDummyServices(3, tasksLimit + 3);

      // check that no dummy services are being moved
      QueryTask query = QueryTask.create(
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestServiceWithStage.State.class,
              TaskState.TaskStage.STARTED))
          .setDirect(true);

      QueryTask response = environment.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() >= tasksLimit + 3;
            }
          });
      assertThat(response.results.documentLinks.size(), is(tasksLimit + 3));
    }

    private void initEnvironment(int count) throws Throwable {
      BasicServiceHost[] hosts = new BasicServiceHost[count];
      for (int i = 0; i < count; i++) {
        hosts[i] = BasicServiceHost.create();
      }

      environment = new BasicHostEnvironment.Builder().hostList(hosts).build();

      Thread.sleep(3000);

      environment.startFactoryServiceSynchronously(TestServiceWithStageFactory.class,
          TestServiceWithStageFactory.SELF_LINK);
      for (int i = 0; i < hosts.length; i++) {
        environment.waitForReplicatedFactoryServices(hosts[i]);
      }

      Thread.sleep(3000);

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
        Operation resultOp = environment.sendPostAndWait(TestServiceWithStageFactory.SELF_LINK, startState);
        assertThat(resultOp.getStatusCode(), is(200));
      }
    }
  }
}
