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
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithWorkQueue;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithWorkQueueFactory;
import com.vmware.photon.controller.common.xenon.helpers.xenon.BasicHostEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static com.google.common.base.Preconditions.checkState;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * This class implements tests for the {@link RateLimitedWorkQueueService} class.
 */
public class RateLimitedWorkQueueServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitedWorkQueueServiceTest.class);

  /**
   * This dummy test case enables IntelliJ to recognize this class as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object creation.
   */
  public class InitializationTest {

    private RateLimitedWorkQueueService rateLimitedWorkQueueService;

    @BeforeMethod
    public void setUpTest() {
      rateLimitedWorkQueueService = new RateLimitedWorkQueueService();
    }

    @Test
    public void testOptions() {
      assertThat(rateLimitedWorkQueueService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link RateLimitedWorkQueueService#handleStart(Operation)}
   * method.
   */
  public class HandleStartTest {

    private BasicServiceHost basicServiceHost;
    private RateLimitedWorkQueueService rateLimitedWorkQueueService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      basicServiceHost = BasicServiceHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      rateLimitedWorkQueueService = new RateLimitedWorkQueueService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        basicServiceHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      BasicServiceHost.destroy(basicServiceHost);
    }

    @Test
    public void testSuccess() throws Throwable {
      basicServiceHost.startServiceSynchronously(rateLimitedWorkQueueService, buildValidStartState());
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testFailureMissingRequiredFieldName(String fieldName) throws Throwable {
      RateLimitedWorkQueueService.State startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      basicServiceHost.startServiceSynchronously(rateLimitedWorkQueueService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return new Object[][]{
          {"pendingTaskServiceQuery"},
          {"startPatchBody"},
          {"concurrencyLimit"},
      };
    }
  }

  /**
   * This class implements tests for the {@link RateLimitedWorkQueueService#handlePatch(Operation)}
   * method.
   */
  public class HandlePatchTest {

    private BasicServiceHost basicServiceHost;
    private RateLimitedWorkQueueService rateLimitedWorkQueueService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      basicServiceHost = BasicServiceHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      rateLimitedWorkQueueService = new RateLimitedWorkQueueService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      basicServiceHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      BasicServiceHost.destroy(basicServiceHost);
    }

    @Test
    public void testValidPatch() throws Throwable {
      basicServiceHost.startServiceSynchronously(rateLimitedWorkQueueService, buildValidStartState());

      RateLimitedWorkQueueService.PatchState patchState = new RateLimitedWorkQueueService.PatchState();
      patchState.pendingTaskServiceDelta = 1;

      basicServiceHost.sendRequestAndWait(Operation
          .createPatch(basicServiceHost, BasicServiceHost.SERVICE_URI)
          .setBody(patchState));

      patchState = new RateLimitedWorkQueueService.PatchState();
      patchState.pendingTaskServiceDelta = -1;
      patchState.runningTaskServiceDelta = 1;

      basicServiceHost.sendRequestAndWait(Operation
          .createPatch(basicServiceHost, BasicServiceHost.SERVICE_URI)
          .setBody(patchState));

      patchState = new RateLimitedWorkQueueService.PatchState();
      patchState.runningTaskServiceDelta = -1;

      basicServiceHost.sendRequestAndWait(Operation
          .createPatch(basicServiceHost, BasicServiceHost.SERVICE_URI)
          .setBody(patchState));
    }

    @Test(dataProvider = "InvalidPatchTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchTransition(RateLimitedWorkQueueService.State startState,
                                           RateLimitedWorkQueueService.PatchState patchState) throws Throwable {

      basicServiceHost.startServiceSynchronously(rateLimitedWorkQueueService, startState);

      basicServiceHost.sendRequestAndWait(Operation
          .createPatch(basicServiceHost, BasicServiceHost.SERVICE_URI)
          .setBody(patchState));
    }

    @DataProvider(name = "InvalidPatchTransitions")
    public Object[][] getInvalidPatchTransitions() {

      RateLimitedWorkQueueService.State startState = buildValidStartState();

      RateLimitedWorkQueueService.PatchState negativePendingPatchState = new RateLimitedWorkQueueService.PatchState();
      negativePendingPatchState.pendingTaskServiceDelta = -1;

      RateLimitedWorkQueueService.PatchState negativeRunningPatchState = new RateLimitedWorkQueueService.PatchState();
      negativeRunningPatchState.runningTaskServiceDelta = -1;

      RateLimitedWorkQueueService.PatchState concurrencyLimitPatchState = new RateLimitedWorkQueueService.PatchState();
      concurrencyLimitPatchState.runningTaskServiceDelta = startState.concurrencyLimit + 1;

      return new Object[][]{

          /**
           * N.B. In some edge cases, it is valid for the pendingTaskServiceCount field to become
           * negative. See the comment in
           * {@link RateLimitedWorkQueueService#validateState(RateLimitedWorkQueueService.State)}.
          */
//          {startState, negativePendingPatchState},
          {startState, negativeRunningPatchState},
          {startState, concurrencyLimitPatchState},
      };
    }
  }

  /**
   * This class implements end-to-end tests for the {@link RateLimitedWorkQueueService} class.
   */
  public class EndToEndTest {

    private final String workQueueName = "test-service-work-queue";

    private final String workQueueSelfLink = UriUtils.buildUriPath(RateLimitedWorkQueueFactoryService.SELF_LINK,
        workQueueName);

    private final Integer stressTestIterations = 100;

    private final Integer stressTestTasksPerIteration = 200;

    private BasicHostEnvironment basicHostEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      BasicServiceHost[] hosts = new BasicServiceHost[1];
      hosts[0] = BasicServiceHost.create();
      basicHostEnvironment = new BasicHostEnvironment.Builder().hostList(hosts).build();
      basicHostEnvironment.startFactoryServiceSynchronously(RateLimitedWorkQueueFactoryService.class,
          RateLimitedWorkQueueFactoryService.SELF_LINK);
      basicHostEnvironment.startFactoryServiceSynchronously(TestServiceWithWorkQueueFactory.class,
          TestServiceWithWorkQueueFactory.SELF_LINK);
      basicHostEnvironment.waitForReplicatedFactoryServices(hosts[0]);

      RateLimitedWorkQueueService.State startState = buildValidStartState();
      startState.documentSelfLink = workQueueName;
      startState.controlFlags = null;
      startState.concurrencyLimit = 10;
      basicHostEnvironment.sendPostAndWait(RateLimitedWorkQueueFactoryService.SELF_LINK, startState);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(TestServiceWithWorkQueue.State.class)
              .build())
          .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
          .build();

      Operation op = basicHostEnvironment.sendPostAndWait(ServiceUriPaths.CORE_QUERY_TASKS, queryTask);
      checkState(op.getStatusCode() == Operation.STATUS_CODE_OK);
      checkState(op.getBody(QueryTask.class).results.documentLinks.size() == 0);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(TestServiceWithWorkQueue.State.class)
              .build())
          .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
          .build();

      Operation op = basicHostEnvironment.sendPostAndWait(ServiceUriPaths.CORE_QUERY_TASKS, queryTask);
      checkState(op.getStatusCode() == Operation.STATUS_CODE_OK);

      for (String documentLink : op.getBody(QueryTask.class).results.documentLinks) {
        basicHostEnvironment.sendDeleteAndWait(documentLink);
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      basicHostEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      for (int i = 0; i < 100; i++) {
        testSingleSuccess();
      }
    }

    private void testSingleSuccess() throws Throwable {
      TestServiceWithWorkQueue.State startState = new TestServiceWithWorkQueue.State();
      startState.workQueueServiceLink = workQueueSelfLink;
      Operation startOp = basicHostEnvironment.sendPostAndWait(TestServiceWithWorkQueueFactory.SELF_LINK, startState);
      String serviceUri = startOp.getBody(ServiceDocument.class).documentSelfLink;

      basicHostEnvironment.waitForServiceState(
          TestServiceWithWorkQueue.State.class,
          serviceUri,
          (state) -> state.taskState.stage == TaskState.TaskStage.FINISHED);
    }

    @Test
    public void testSuccessWithConcurrency() throws Throwable {

      TestServiceWithWorkQueue.State startState = new TestServiceWithWorkQueue.State();
      startState.workQueueServiceLink = workQueueSelfLink;

      for (int i = 0; i < 10; i++) {
        Operation op = basicHostEnvironment.sendPostAndWait(TestServiceWithWorkQueueFactory.SELF_LINK, startState);
        checkState(op.getStatusCode() == Operation.STATUS_CODE_OK);
      }

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(TestServiceWithWorkQueue.State.class)
              .addCompositeFieldClause("taskState", "stage",
                  QueryTask.QuerySpecification.toMatchValue(TaskState.TaskStage.FINISHED))
              .build())
          .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
          .build();

      basicHostEnvironment.waitForQuery(queryTask,
          (query) -> query.results.documentLinks.size() == 10);
    }

    @Test(enabled = false)
    public void stressTest() throws Throwable {
      for (int i = 0; i < stressTestIterations; i++) {
        runStressIteration(i);
      }
    }

    private void runStressIteration(int iteration) throws Throwable {

      logger.info("Starting stress iteration " + iteration);

      TestServiceWithWorkQueue.State startState = new TestServiceWithWorkQueue.State();
      startState.workQueueServiceLink = workQueueSelfLink;

      for (int i = 0; i < stressTestTasksPerIteration; i++) {
        Operation op = basicHostEnvironment.sendPostAndWait(TestServiceWithWorkQueueFactory.SELF_LINK, startState);
        checkState(op.getStatusCode() == Operation.STATUS_CODE_OK);
      }

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(TestServiceWithWorkQueue.State.class)
              .addCompositeFieldClause("taskState", "stage",
                  QueryTask.QuerySpecification.toMatchValue(TaskState.TaskStage.FINISHED))
              .build())
          .build();

      basicHostEnvironment.waitForQuery(queryTask,
          (query) -> query.results.documentLinks.size() == (1 + iteration) * stressTestTasksPerIteration);

      logger.info("Finished stress iteration " + iteration);

      RateLimitedWorkQueueService.State queueState = basicHostEnvironment.getServiceState(workQueueSelfLink,
          RateLimitedWorkQueueService.State.class);

      checkState(queueState.pendingTaskServiceCount == 0);
      checkState(queueState.runningTaskServiceCount == 0);
    }
  }

  private RateLimitedWorkQueueService.State buildValidStartState() {

    QueryTask.Query pendingTaskServiceQuery = QueryTask.Query.Builder.create()
        .addKindFieldClause(TestServiceWithWorkQueue.State.class)
        .addCompositeFieldClause("taskState", "stage",
            QueryTask.QuerySpecification.toMatchValue(TaskState.TaskStage.CREATED))
        .build();

    RateLimitedWorkQueueService.State startState = new RateLimitedWorkQueueService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.pendingTaskServiceQuery = pendingTaskServiceQuery;
    startState.startPatchBody = Utils.toJson(TestServiceWithWorkQueue.buildPatch(TaskState.TaskStage.STARTED));
    startState.concurrencyLimit = 2;
    return startState;
  }
}
