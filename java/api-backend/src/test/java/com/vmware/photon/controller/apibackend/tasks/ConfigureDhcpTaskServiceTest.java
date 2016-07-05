/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpTask;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.ConfigureDhcpTaskService}.
 */
public class ConfigureDhcpTaskServiceTest {

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the initialization of service itself.
   */
  public static class InitializationTest {
    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);

      ConfigureDhcpTaskService service = new ConfigureDhcpTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static ConfigureDhcpTaskService configureDhcpTaskService;

    @BeforeClass
    public void setupClass() throws Throwable {
      host = new TestHost.Builder().build();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      host.stop();
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      configureDhcpTaskService = new ConfigureDhcpTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testSuccessfulHandleStart(ConfigureDhcpTask.TaskState.TaskStage startStage,
                                          ConfigureDhcpTask.TaskState.SubStage startSubStage,
                                          ConfigureDhcpTask.TaskState.TaskStage expectedStage,
                                          ConfigureDhcpTask.TaskState.SubStage expectedSubStage) throws Throwable {

      ConfigureDhcpTask createdState = createConfigureDhcpTaskService(host, configureDhcpTaskService,
          startStage, startSubStage, ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureDhcpTask savedState = host.getServiceState(ConfigureDhcpTask.class, createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createConfigureDhcpTaskService(host, configureDhcpTaskService, ConfigureDhcpTask.TaskState.TaskStage.STARTED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE, 0);
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {ConfigureDhcpTask.TaskState.TaskStage.CREATED, null,
              ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {ConfigureDhcpTask.TaskState.TaskStage.FINISHED, null,
              ConfigureDhcpTask.TaskState.TaskStage.FINISHED, null},
          {ConfigureDhcpTask.TaskState.TaskStage.CANCELLED, null,
              ConfigureDhcpTask.TaskState.TaskStage.CANCELLED, null},
          {ConfigureDhcpTask.TaskState.TaskStage.FAILED, null,
              ConfigureDhcpTask.TaskState.TaskStage.FAILED, null}
      };
    }

    @Test(dataProvider = "notEmptyFields")
    public void testNotEmptyFields(String fieldName, String expectedErrorMessage) throws Throwable {
      ConfigureDhcpTask startState = new ConfigureDhcpTask();

      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (!field.getName().equals(fieldName)) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      startState.taskState = new ConfigureDhcpTask.TaskState();
      startState.taskState.stage = ConfigureDhcpTask.TaskState.TaskStage.CREATED;
      startState.taskState.subStage = ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE;

      try {
        host.startServiceSynchronously(configureDhcpTaskService, startState);
        fail("should have failed due to violation of not empty restraint");
      } catch (Exception e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }


    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][] {
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"},
          {"dhcpServerAddresses", "dhcpServerAddresses cannot be null"},
      };
    }
  }

  /**
   * Tests for handlePatch.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static ConfigureDhcpTaskService configureDhcpTaskService;

    @BeforeClass
    public void setupClass() throws Throwable {
      host = new TestHost.Builder().build();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      host.stop();
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      configureDhcpTaskService = new ConfigureDhcpTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }


    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(ConfigureDhcpTask.TaskState.TaskStage startStage,
                                         ConfigureDhcpTask.TaskState.SubStage startSubStage,
                                         ConfigureDhcpTask.TaskState.TaskStage targetStage,
                                         ConfigureDhcpTask.TaskState.SubStage targetSubStage) throws Throwable {
      ConfigureDhcpTask createdState = createConfigureDhcpTaskService(host,
          configureDhcpTaskService,
          ConfigureDhcpTask.TaskState.TaskStage.CREATED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      ConfigureDhcpTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      ConfigureDhcpTask savedState = host.getServiceState(ConfigureDhcpTask.class, createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransition")
    public void testInvalidStageTransition(ConfigureDhcpTask.TaskState.TaskStage startStage,
                                           ConfigureDhcpTask.TaskState.SubStage startSubStage,
                                           ConfigureDhcpTask.TaskState.TaskStage targetStage,
                                           ConfigureDhcpTask.TaskState.SubStage targetSubStage) throws Throwable {
      ConfigureDhcpTask createdState = createConfigureDhcpTaskService(host,
          configureDhcpTaskService,
          ConfigureDhcpTask.TaskState.TaskStage.CREATED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      ConfigureDhcpTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName) throws Throwable {
      ConfigureDhcpTask createdState = createConfigureDhcpTaskService(host,
          configureDhcpTaskService,
          ConfigureDhcpTask.TaskState.TaskStage.CREATED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureDhcpTask patchState = buildPatchState(ConfigureDhcpTask.TaskState.TaskStage.STARTED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* cannot be set or changed in a patch",
        dataProvider = "writeOnceFields")
    public void testChangeWriteOnceFields(String fieldName) throws Throwable {
      ConfigureDhcpTask createdState = createConfigureDhcpTaskService(host,
          configureDhcpTaskService,
          ConfigureDhcpTask.TaskState.TaskStage.CREATED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureDhcpTask patchState = buildPatchState(ConfigureDhcpTask.TaskState.TaskStage.STARTED,
          ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);
      host.sendRequestAndWait(patch);

      patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);
      host.sendRequestAndWait(patch);
    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransition() {
      return new Object[][] {
          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
              ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},
          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              ConfigureDhcpTask.TaskState.TaskStage.FINISHED,
              null},

          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
              ConfigureDhcpTask.TaskState.TaskStage.FAILED,
              null},
          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              ConfigureDhcpTask.TaskState.TaskStage.FAILED,
              null},

          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
              ConfigureDhcpTask.TaskState.TaskStage.CANCELLED,
              null},
          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              ConfigureDhcpTask.TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @DataProvider(name = "invalidStageTransition")
    public Object[][] getInvalidStageTransition() {
      return new Object[][] {
          {ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
      };
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags"},
          {"nsxManagerEndpoint"},
          {"username"},
          {"password"},
          {"dhcpServerAddresses"},
      };
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"dhcpRelayProfileId"},
          {"dhcpRelayServiceId"},
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  ConfigureDhcpTask.TaskState.TaskStage targetStage,
                                  ConfigureDhcpTask.TaskState.SubStage targetSubStage) throws Throwable {

      Pair<TaskState.TaskStage, ConfigureDhcpTask.TaskState.SubStage>[] transitionSequence = new Pair[]{
          Pair.of(ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE),
          Pair.of(ConfigureDhcpTask.TaskState.TaskStage.STARTED,
              ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE),
          Pair.of(ConfigureDhcpTask.TaskState.TaskStage.FINISHED,
              null)
      };

      for (Pair<ConfigureDhcpTask.TaskState.TaskStage, ConfigureDhcpTask.TaskState.SubStage> state :
          transitionSequence) {
        ConfigureDhcpTask patchState = buildPatchState(state.getLeft(), state.getRight());
        Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
            .setBody(patchState);

        Operation result = host.sendRequestAndWait(patch);
        assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

        ConfigureDhcpTask savedState = host.getServiceState(ConfigureDhcpTask.class, documentSelfLink);
        assertThat(savedState.taskState.stage, is(state.getLeft()));
        assertThat(savedState.taskState.subStage, is(state.getRight()));

        if (savedState.taskState.stage == targetStage && savedState.taskState.subStage == targetSubStage) {
          break;
        }
      }
    }
  }

  private static ConfigureDhcpTask createConfigureDhcpTaskService(
      TestHost testHost,
      ConfigureDhcpTaskService service,
      ConfigureDhcpTask.TaskState.TaskStage stage,
      ConfigureDhcpTask.TaskState.SubStage subStage,
      int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service,
        buildStartState(stage, subStage, controlFlag));
    return result.getBody(ConfigureDhcpTask.class);
  }

  private static ConfigureDhcpTask buildStartState(ConfigureDhcpTask.TaskState.TaskStage startStage,
                                                      ConfigureDhcpTask.TaskState.SubStage subStage,
                                                      int controlFlag) {

    ConfigureDhcpTask startState = new ConfigureDhcpTask();

    startState.taskState = new ConfigureDhcpTask.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;

    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.dhcpServerAddresses = new ArrayList<>();
    startState.dhcpServerAddresses.add("1.2.3.4");
    startState.controlFlags = controlFlag;

    return startState;
  }

  private static ConfigureDhcpTask buildPatchState(ConfigureDhcpTask.TaskState.TaskStage patchStage,
                                                   ConfigureDhcpTask.TaskState.SubStage patchSubStage) {

    ConfigureDhcpTask patchState = new ConfigureDhcpTask();
    patchState.taskState = new ConfigureDhcpTask.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
