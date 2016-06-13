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

package com.vmware.photon.controller.deployer.xenon.entity;

import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link ContainerService} class.
 */
public class ContainerServiceTest {

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ContainerService containerService;

    @BeforeMethod
    public void setUpTest() {
      containerService = new ContainerService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(containerService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private ContainerService containerService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      containerService = new ContainerService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStates")
    public void testValidStartState(String containerId) throws Throwable {
      ContainerService.State startState = TestHelper.getContainerServiceStartState();
      startState.containerId = containerId;
      Operation startOperation = testHost.startServiceSynchronously(containerService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ContainerService.State savedState = testHost.getServiceState(ContainerService.State.class);
      assertThat(savedState.containerTemplateServiceLink, is("CONTAINER_TEMPLATE_SERVICE_LINK"));
      assertThat(savedState.vmServiceLink, is("VM_SERVICE_LINK"));
      assertThat(savedState.containerId, is(containerId));
    }

    @DataProvider(name = "ValidStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {null},
          {"CONTAINER_ID"},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      ContainerService.State startState = TestHelper.getContainerServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(containerService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(ContainerService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private ContainerService containerService;
    private TestHost testHost;

    private void createContainerService() throws Throwable {
      ContainerService.State startState = TestHelper.getContainerServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(containerService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      containerService = new ContainerService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateContainerTemplateServiceLinkFieldSet() throws Throwable {
      createContainerService();
      ContainerService.State patchState = new ContainerService.State();
      patchState.containerTemplateServiceLink = "CONTAINER_TEMPLATE_SERVICE_LINK";

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateVmServiceLinkFieldSet() throws Throwable {
      createContainerService();
      ContainerService.State patchState = new ContainerService.State();
      patchState.vmServiceLink = "VM_SERVICE_LINK";

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @Test(dataProvider = "ValidContainerIdPatchValues")
    public void testValidPatchStateContainerIdFieldSet(String containerId) throws Throwable {
      createContainerService();
      ContainerService.State patchState = new ContainerService.State();
      patchState.containerId = containerId;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));

      ContainerService.State savedState = testHost.getServiceState(ContainerService.State.class);
      assertThat(savedState.containerId, is(containerId));
    }

    @DataProvider(name = "ValidContainerIdPatchValues")
    public Object[][] getValidContainerIdPatchValues() {
      return new Object[][]{
          {null},
          {"CONTAINER_ID"},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateOverwriteExistingContainerId() throws Throwable {
      ContainerService.State startState = TestHelper.getContainerServiceStartState();
      startState.containerId = "CONTAINER_ID";
      Operation startOperation = testHost.startServiceSynchronously(containerService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ContainerService.State patchState = new ContainerService.State();
      patchState.containerId = "CONTAINER_ID";

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }
  }
}
