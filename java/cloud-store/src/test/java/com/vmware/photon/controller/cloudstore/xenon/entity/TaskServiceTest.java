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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TaskService}.
 */
public class TaskServiceTest {

  private XenonRestClient xenonRestClient;
  private BasicServiceHost host;
  private TaskService service;
  private TaskService.State testState;

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
      service = new TaskService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskService();
      host = BasicServiceHost.create(
          null,
          TaskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new TaskService.State();
      testState.entityId = UUID.randomUUID().toString();
      testState.entityKind = UUID.randomUUID().toString();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
    }

    /**
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new TaskServiceFactory(), null);

      Operation result = xenonRestClient.post(TaskServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      TaskService.State createdState = result.getBody(TaskService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      TaskService.State savedState = host.getServiceState(TaskService.State.class, createdState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskService();
      host = BasicServiceHost.create();
      testState = new TaskService.State();
      testState.entityId = UUID.randomUUID().toString();
      testState.entityKind = UUID.randomUUID().toString();
      testState.state = TaskService.State.TaskState.STARTED;
      testState.steps = new ArrayList<>();
      TaskService.State.Step step = new TaskService.State.Step();
      step.operation = com.vmware.photon.controller.api.Operation.CREATE_VM.getOperation();
      step.state = TaskService.State.StepState.QUEUED;
      step.warnings = new ArrayList<>();
      TaskService.State.StepError stepError = new TaskService.State.StepError();
      stepError.code = UUID.randomUUID().toString();
      step.warnings.add(stepError);
      testState.steps.add(step);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation which should succeed.
     *
     * @throws Throwable
     */
    @Test
    public void testPatchSuccess() throws Throwable {
      Operation result = host.startServiceSynchronously(service, testState);

      TaskService.State taskState = result.getBody(TaskService.State.class);

      assertThat(taskState.entityId, is(testState.entityId));
      assertThat(taskState.state, is(testState.state));
      assertThat(taskState.steps.get(0).operation, is(testState.steps.get(0).operation));
      assertThat(taskState.steps.get(0).state, is(testState.steps.get(0).state));
      assertThat(taskState.steps.get(0).startedTime, is(testState.steps.get(0).startedTime));
      assertThat(taskState.steps.get(0).warnings.get(0).code,
          is(testState.steps.get(0).warnings.get(0).code));
      assertThat(taskState.steps.get(0).errors, is(nullValue()));

      TaskService.State patchState = new TaskService.State();
      patchState.state = TaskService.State.TaskState.COMPLETED;
      patchState.entityId = UUID.randomUUID().toString();

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);

      result = host.sendRequestAndWait(patch);

      taskState = result.getBody(TaskService.State.class);

      assertThat(taskState.entityId, is(patchState.entityId));
      assertThat(taskState.state, is(patchState.state));
      assertThat(taskState.steps, is(nullValue()));

    }

    @Test
    public void testUpdateStep() throws Throwable {
      Operation result = host.startServiceSynchronously(service, testState);

      TaskService.State taskState = result.getBody(TaskService.State.class);

      assertThat(taskState.entityId, is(testState.entityId));
      assertThat(taskState.state, is(testState.state));
      assertThat(taskState.steps.get(0).operation, is(testState.steps.get(0).operation));
      assertThat(taskState.steps.get(0).state, is(testState.steps.get(0).state));
      assertThat(taskState.steps.get(0).startedTime, is(testState.steps.get(0).startedTime));
      assertThat(taskState.steps.get(0).warnings.get(0).code,
          is(testState.steps.get(0).warnings.get(0).code));
      assertThat(taskState.steps.get(0).errors, is(nullValue()));

      TaskService.State.Step step = new TaskService.State.Step();
      step.operation = testState.steps.get(0).operation;
      TaskService.StepUpdate stepUpdate = new TaskService.StepUpdate(step);
      stepUpdate.step.startedTime = DateTime.now().toDate();
      stepUpdate.step.state = TaskService.State.StepState.STARTED;
      TaskService.State.StepError stepError = new TaskService.State.StepError();
      stepError.code = UUID.randomUUID().toString();
      stepUpdate.step.warnings = testState.steps.get(0).warnings;
      stepUpdate.step.warnings.add(stepError);
      stepUpdate.step.errors = new ArrayList<>();
      stepUpdate.step.errors.add(stepError);
      stepUpdate.step.endTime = DateTime.now().toDate();

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, BasicServiceHost.SERVICE_URI, null))
          .setBody(stepUpdate);

      result = host.sendRequestAndWait(patch);

      taskState = result.getBody(TaskService.State.class);

      assertThat(taskState.entityId, is(testState.entityId));
      assertThat(taskState.state, is(testState.state));
      assertThat(taskState.steps.get(0).operation, is(stepUpdate.step.operation));
      assertThat(taskState.steps.get(0).state, is(TaskService.State.StepState.STARTED));
      assertThat(taskState.steps.get(0).startedTime, is(stepUpdate.step.startedTime));
      assertThat(taskState.steps.get(0).warnings
              .stream().anyMatch(w -> w.code.equals(testState.steps.get(0).warnings.get(0).code)),
          is(true));
      assertThat(taskState.steps.get(0).warnings
              .stream().anyMatch(w -> w.code.equals(stepError.code)),
          is(true));
      assertThat(taskState.steps.get(0).warnings.get(0).code,
          is(testState.steps.get(0).warnings.get(0).code));
      assertThat(taskState.steps.get(0).endTime, is(stepUpdate.step.endTime));

      taskState = host.getServiceState(TaskService.State.class, taskState.documentSelfLink);

      assertThat(taskState.entityId, is(testState.entityId));
      assertThat(taskState.state, is(testState.state));
      assertThat(taskState.steps.get(0).operation, is(stepUpdate.step.operation));
      assertThat(taskState.steps.get(0).state, is(TaskService.State.StepState.STARTED));
      assertThat(taskState.steps.get(0).startedTime, is(stepUpdate.step.startedTime));
      assertThat(taskState.steps.get(0).warnings
              .stream().anyMatch(w -> w.code.equals(testState.steps.get(0).warnings.get(0).code)),
          is(true));
      assertThat(taskState.steps.get(0).warnings
              .stream().anyMatch(w -> w.code.equals(stepError.code)),
          is(true));
      assertThat(taskState.steps.get(0).warnings.get(0).code,
          is(testState.steps.get(0).warnings.get(0).code));
      assertThat(taskState.steps.get(0).endTime, is(stepUpdate.step.endTime));
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskService();
      host = BasicServiceHost.create(
          null,
          TaskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new TaskService.State();
      testState.entityId = UUID.randomUUID().toString();
      testState.entityKind = UUID.randomUUID().toString();

      host.startServiceSynchronously(new TaskServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
    }

    /**
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          TaskServiceFactory.SELF_LINK,
          testState,
          TaskService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE));
    }

    /**
     * Test default expiration is not applied if it is already specified in delete operation state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInDeleteOperation() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          TaskServiceFactory.SELF_LINK,
          testState,
          TaskService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE));
    }

    /**
     * Test expiration of deleted document using default value.
     *
     * @throws Throwable
     */
    @Test
    public void testDeleteWithDefaultExpiration() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonRestClient,
          host,
          TaskServiceFactory.SELF_LINK,
          testState,
          TaskService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }
}
