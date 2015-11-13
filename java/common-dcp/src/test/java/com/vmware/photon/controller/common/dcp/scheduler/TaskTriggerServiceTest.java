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

package com.vmware.photon.controller.common.dcp.scheduler;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceConfiguration;
import com.vmware.dcp.common.ServiceDocumentQueryResult;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.helpers.services.TestServiceWithStage;
import com.vmware.photon.controller.common.dcp.helpers.services.TestServiceWithStageFactory;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TaskTriggerService}.
 */
public class TaskTriggerServiceTest {

  private BasicServiceHost host;
  private TaskTriggerService service;
  private String selfLink = TaskTriggerFactoryService.SELF_LINK + "/test-service-scheduler";

  private TaskTriggerService.State buildValidStartupState() {
    TestServiceWithStage.State nestedState = new TestServiceWithStage.State();
    nestedState.taskInfo = new TaskState();
    nestedState.taskInfo.stage = TaskState.TaskStage.STARTED;

    TaskTriggerService.State state = new TaskTriggerService.State();
    state.factoryServiceLink = TestServiceWithStageFactory.SELF_LINK;
    state.serializedTriggerState = Utils.toJson(nestedState);
    state.triggerStateClassName = nestedState.getClass().getTypeName();
    state.taskExpirationAgeMillis = 5 * 60 * 60 * 1000;

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = true)
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new TaskTriggerService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE,
          Service.ServiceOption.PERSISTENCE);
      assertThat(service.getOptions(), is(expected));
      assertThat(service.getMaintenanceIntervalMicros(),
          is(TimeUnit.MILLISECONDS.toMicros(TaskTriggerService.DEFAULT_MAINTENANCE_INTERVAL_MILLIS)));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TaskTriggerService();
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
      TaskTriggerService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState, selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      TaskTriggerService.State savedState = host.getServiceState(TaskTriggerService.State.class, selfLink);
      assertThat(savedState.documentSelfLink, is(selfLink));
      assertThat(Utils.toJson(savedState.serializedTriggerState), is(Utils.toJson(startState.serializedTriggerState)));
      assertThat(savedState.factoryServiceLink, is(startState.factoryServiceLink));
      assertThat(savedState.taskExpirationAgeMillis, is(startState.taskExpirationAgeMillis));

      ServiceConfiguration config = host.getServiceState(ServiceConfiguration.class, selfLink + "/config");
      assertThat(config.maintenanceIntervalMicros,
          is(TimeUnit.MILLISECONDS.toMicros(TaskTriggerService.DEFAULT_MAINTENANCE_INTERVAL_MILLIS)));
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "NotBlankFields",
        expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = "(.* cannot be null|.* cannot be blank)")
    public void testNotBlankFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State startState = buildValidStartupState();

      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, value);

      host.startServiceSynchronously(service, startState, selfLink);
    }

    @DataProvider(name = "NotBlankFields")
    public Object[][] getNotBlankFieldsParams() {
      return new Object[][]{
          {"serializedTriggerState", null},
          {"serializedTriggerState", ""},
          {"triggerStateClassName", null},
          {"triggerStateClassName", ""},
          {"factoryServiceLink", null},
          {"factoryServiceLink", ""}
      };
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "AutoInitializedFields")
    public void testAutoInitializedFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State startState = buildValidStartupState();
      Field fieldObj = startState.getClass().getField(fieldName);
      fieldObj.set(startState, null);

      Operation startOp = host.startServiceSynchronously(service, startState, selfLink);
      assertThat(startOp.getStatusCode(), is(200));

      TaskTriggerService.State savedState = host.getServiceState(TaskTriggerService.State.class, selfLink);
      assertThat(fieldObj.get(savedState), is(value));
    }

    @DataProvider(name = "AutoInitializedFields")
    public Object[][] getAutoInitializedFieldsParams() {
      return new Object[][]{
          {"taskExpirationAgeMillis", TaskTriggerService.DEFAULT_TASK_EXPIRATION_AGE_MILLIS}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    TaskTriggerService.State serviceState;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();
      host.startFactoryServiceSynchronously(new TestServiceWithStageFactory(), TestServiceWithStageFactory.SELF_LINK);

      service = new TaskTriggerService();
      serviceState = buildValidStartupState();
      host.startServiceSynchronously(service, serviceState, selfLink);
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
      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody("invalid body");

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "NotBlankFields",
        expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = ".* cannot be blank")
    public void testNotBlankFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State patchState = new TaskTriggerService.State();
      Field fieldObj = patchState.getClass().getField(fieldName);
      fieldObj.set(patchState, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(patchState);
      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "NotBlankFields")
    public Object[][] getNotBlankFieldsParams() {
      return new Object[][]{
          {"serializedTriggerState", ""},
          {"triggerStateClassName", ""},
          {"factoryServiceLink", ""}
      };
    }

    /**
     * Tests that exception is raised for all fields that expect a positive value.
     *
     * @param fieldName
     * @param value
     * @throws Throwable
     */
    @Test(dataProvider = "PositiveFields",
        expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = ".* must be greater than zero")
    public void testPositiveFields(String fieldName, Object value) throws Throwable {
      TaskTriggerService.State patchState = new TaskTriggerService.State();
      Field fieldObj = patchState.getClass().getField(fieldName);
      fieldObj.set(patchState, value);

      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(patchState);
      host.sendRequestAndWait(op);
    }

    @DataProvider(name = "PositiveFields")
    public Object[][] getPositiveFieldsParams() {
      return new Object[][]{
          {"taskExpirationAgeMillis", 0},
          {"taskExpirationAgeMillis", -10}
      };
    }

    /**
     * Test patch that will trigger a service instance.
     *
     * @throws Throwable
     */
    @Test
    public void testTriggerPatch() throws Throwable {
      TaskTriggerService.State state = new TaskTriggerService.State();
      state.triggerIntervalMillis = 10;
      Operation op = Operation
          .createPatch(UriUtils.buildUri(host, selfLink, null))
          .setBody(state);
      Operation result = host.sendRequestAndWait(op);
      TaskTriggerService.State newServiceState = result.getBody(TaskTriggerService.State.class);
      assertThat(newServiceState.triggerIntervalMillis, is(10));

      assertThat(result.getStatusCode(), is(200));

      ServiceDocumentQueryResult doc = host.waitForState(
          serviceState.factoryServiceLink,
          ServiceDocumentQueryResult.class,
         (ServiceDocumentQueryResult o) -> o.documentCount >= 1);
      assertThat(doc.documentCount, is(1L));

      TestServiceWithStage.State triggeredService = host.getServiceState(
          TestServiceWithStage.State.class, doc.documentLinks.get(0));
      assertThat(triggeredService.taskInfo.stage, is(TaskState.TaskStage.STARTED));
    }
  }
}
