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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.helpers.xenon.BasicHostEnvironment;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class implements tests for the {@link RateLimitedWorkQueueService} class.
 */
public class RateLimitedWorkQueueServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitedWorkQueueServiceTest.class);

  private static final int CONCURRENCY_LIMIT = 8;

  private static final int SERVICE_HOST_COUNT = 3;

  private static final int TASK_SERVICE_COUNT = 1000;

  /**
   * This class implements a simple test class which self-patches to the FINISHED state upon
   * starting and notifies a parent task upon completion.
   */
  public static class ValidationTaskService extends StatefulService {

    /**
     * This class defines the document state associated with a {@link ValidationTaskService} instance.
     */
    public static class State extends TaskServiceState {

      /**
       * This field represents the document self-link of the parent service to be notified when
       * the current task reaches the FINISHED state.
       */
      public String parentTaskServiceLink;
    }

    public ValidationTaskService() {
      super(State.class);
      super.toggleOption(ServiceOption.PERSISTENCE, true);
    }

    @Override
    public void handlePatch(Operation patchOp) {
      State currentState = getState(patchOp);
      TaskServiceState patchState = patchOp.getBody(TaskServiceState.class);
      checkState(patchState.taskState.stage.ordinal() > currentState.taskState.stage.ordinal());
      currentState.taskState = patchState.taskState;
      patchOp.complete();

      switch (currentState.taskState.stage) {
        case STARTED:
          sendStageProgressPatch(TaskState.TaskStage.FINISHED);
          break;
        case FINISHED:
          sendRequest(Operation.createPatch(this, currentState.parentTaskServiceLink).setBody(currentState));
          break;
      }
    }

    private void sendStageProgressPatch(TaskState.TaskStage stage) {
      TaskServiceState patchState = new TaskServiceState();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = stage;
      sendRequest(Operation.createPatch(this, getSelfLink()).setBody(patchState));
    }
  }

  /**
   * This class implements a factory for the {@link ValidationTaskService} class.
   */
  public static class ValidationTaskFactoryService extends FactoryService {

    public static final String SELF_LINK = "/validation";

    public ValidationTaskFactoryService() {
      super(ValidationTaskService.State.class);
    }

    @Override
    public Service createServiceInstance() {
      return new ValidationTaskService();
    }
  }

  /**
   * This class implements a simple aggregator for child task completion notifications.
   */
  public static class ChildTaskCounterService extends StatefulService {

    /**
     * This class defines the document state associated with a {@link ChildTaskCounterService} instance.
     */
    public static class State extends ServiceDocument {

      /**
       * This value represents the number of child task completion patches which have been
       * received.
       */
      public Integer childTaskCompletionCount;
    }

    public ChildTaskCounterService() {
      super(State.class);
      super.toggleOption(ServiceOption.OWNER_SELECTION, true);
      super.toggleOption(ServiceOption.REPLICATION, true);
    }

    @Override
    public void handlePatch(Operation patchOp) {
      State currentState = getState(patchOp);
      currentState.childTaskCompletionCount++;
      patchOp.complete();
    }
  }

  /**
   * This class implements a factory for the {@link ChildTaskCounterService} class.
   */
  public static class ChildTaskCounterFactoryService extends FactoryService {

    public static final String SELF_LINK = "/aggregators";

    public ChildTaskCounterFactoryService() {
      super(ChildTaskCounterService.State.class);
    }

    @Override
    public Service createServiceInstance() {
      return new ChildTaskCounterService();
    }
  }

  /**
   * This dummy test enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements end-to-end tests for the work queue.
   */
  public class EndToEndTest {

    private BasicServiceHost[] hosts;
    private BasicHostEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      hosts = new BasicServiceHost[SERVICE_HOST_COUNT];
      for (int i = 0; i < SERVICE_HOST_COUNT; i++) {
        hosts[i] = BasicServiceHost.create();
      }

      testEnvironment = new BasicHostEnvironment.Builder().hostList(hosts).build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testTaskServicesTransitioned() throws Throwable {

      testEnvironment.startFactoryServiceSynchronously(ChildTaskCounterFactoryService.class,
          ChildTaskCounterFactoryService.SELF_LINK);

      startWorkQueueServices(testEnvironment);
      String aggregatorServiceLink = startTaskServices(testEnvironment);
      testEnvironment.waitForServiceState(ChildTaskCounterService.State.class, aggregatorServiceLink,
          (state) -> state.childTaskCompletionCount == TASK_SERVICE_COUNT);
    }

    @Test
    public void testTaskServicesTransitionedWithReplay() throws Throwable {

      testEnvironment.startFactoryServiceSynchronously(ChildTaskCounterFactoryService.class,
          ChildTaskCounterFactoryService.SELF_LINK);

      String aggregatorServiceLink = startTaskServices(testEnvironment);
      startWorkQueueServices(testEnvironment);
      testEnvironment.waitForServiceState(ChildTaskCounterService.State.class, aggregatorServiceLink,
          (state) -> state.childTaskCompletionCount == TASK_SERVICE_COUNT);
    }
  }

  /**
   * This class implements stress tests for the work queue.
   */
  public class StressTest {

    private static final int STRESS_TEST_BATCH_COUNT = 100;

    private BasicServiceHost[] hosts;
    private BasicHostEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      hosts = new BasicServiceHost[SERVICE_HOST_COUNT];
      for (int i = 0; i < SERVICE_HOST_COUNT; i++) {
        hosts[i] = BasicServiceHost.create();
      }

      testEnvironment = new BasicHostEnvironment.Builder().hostList(hosts).build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    @Test(enabled = false)
    public void stressTest() throws Throwable {

      logger.info("Starting test runtime: " + Utils.getNowMicrosUtc());

      testEnvironment.startFactoryServiceSynchronously(ChildTaskCounterFactoryService.class,
          ChildTaskCounterFactoryService.SELF_LINK);

      //
      // Start some task services before starting the work queue services and wait for them to
      // reach FINISHED stage.
      //

      String aggregatorServiceLink = startTaskServices(testEnvironment);
      startWorkQueueServices(testEnvironment);
      testEnvironment.waitForServiceState(ChildTaskCounterService.State.class, aggregatorServiceLink,
          (state) -> state.childTaskCompletionCount == TASK_SERVICE_COUNT);

      //
      // Create a lot of task services and wait for them to reach FINISHED stage.
      //

      for (int i = 0; i < STRESS_TEST_BATCH_COUNT; i++) {
        logger.info("Starting batch iteration " + i + ": " + Utils.getNowMicrosUtc());
        aggregatorServiceLink = startTaskServiceBatch(testEnvironment);
        testEnvironment.waitForServiceState(ChildTaskCounterService.State.class, aggregatorServiceLink,
            (state) -> state.childTaskCompletionCount == TASK_SERVICE_COUNT);
      }

      logger.info("Finished test runtime: " + Utils.getNowMicrosUtc());
    }
  }

  private void startWorkQueueServices(BasicHostEnvironment testEnvironment) throws Throwable {

    // Start the factory service
    testEnvironment.startFactoryServiceSynchronously(RateLimitedWorkQueueFactoryService.class,
        RateLimitedWorkQueueFactoryService.SELF_LINK);

    // Start the work queue services
    RateLimitedWorkQueueService.State startState = new RateLimitedWorkQueueService.State();
    startState.workItemKind = Utils.buildKind(ValidationTaskService.State.class);
    startState.concurrencyLimit = CONCURRENCY_LIMIT;

    for (int i = 0; i < SERVICE_HOST_COUNT; i++) {
      BasicServiceHost host = testEnvironment.getHosts()[i];
      Operation result = host.sendRequestAndWait(Operation
          .createPost(UriUtils.buildUri(host, RateLimitedWorkQueueFactoryService.SELF_LINK))
          .setBody(startState));
      checkState(result.getStatusCode() == 200);
    }
  }

  private String startTaskServices(BasicHostEnvironment testEnvironment) throws Throwable {

    // Start the factory service
    testEnvironment.startFactoryServiceSynchronously(ValidationTaskFactoryService.class,
        ValidationTaskFactoryService.SELF_LINK);

    // Start the task services
    return startTaskServiceBatch(testEnvironment);
  }

  private String startTaskServiceBatch(BasicHostEnvironment testEnvironment) throws Throwable {

    // Start a child task counter service instance.
    ChildTaskCounterService.State aggregatorStartState = new ChildTaskCounterService.State();
    aggregatorStartState.childTaskCompletionCount = 0;
    Operation op = testEnvironment.sendPostAndWait(ChildTaskCounterFactoryService.SELF_LINK, aggregatorStartState);
    checkState(op.getStatusCode() == 200);
    String parentTaskServiceLink = op.getBody(ServiceDocument.class).documentSelfLink;

    ValidationTaskService.State startState = new ValidationTaskService.State();
    startState.taskState = new TaskState();
    startState.taskState.stage = TaskState.TaskStage.CREATED;
    startState.parentTaskServiceLink = parentTaskServiceLink;

    for (int i = 0; i < TASK_SERVICE_COUNT; i++) {
      BasicServiceHost host = testEnvironment.getHosts()[i % SERVICE_HOST_COUNT];
      Operation result = host.sendRequestAndWait(Operation
          .createPost(UriUtils.buildUri(host, ValidationTaskFactoryService.SELF_LINK))
          .setBody(startState));
      checkState(result.getStatusCode() == 200);
    }

    return parentTaskServiceLink;
  }
}
