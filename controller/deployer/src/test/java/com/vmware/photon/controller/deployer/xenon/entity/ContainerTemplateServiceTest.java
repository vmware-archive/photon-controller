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

import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
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
 * This class implements tests for {@link ContainerTemplateService}.
 */
public class ContainerTemplateServiceTest {

  /**
   * This method makes the class look like a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private ContainerTemplateService containerTemplateService;

    @BeforeMethod
    public void setUpTest() {
      containerTemplateService = new ContainerTemplateService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(containerTemplateService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private ContainerTemplateService containerTemplateService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      containerTemplateService = new ContainerTemplateService();
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

    @Test
    public void testValidStartState() throws Throwable {
      ContainerTemplateService.State startState = TestHelper.getContainerTemplateServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(containerTemplateService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ContainerTemplateService.State savedState = testHost.getServiceState(ContainerTemplateService.State.class);
      assertThat(savedState.name, is("NAME"));
      assertThat(savedState.containerImage, is("IMAGE"));
      assertThat(savedState.isReplicated, is(false));
      assertThat(savedState.isPrivileged, is(false));
      assertThat(savedState.portBindings.get(5432), is(5432));
      assertThat(savedState.cpuCount, is(1));
      assertThat(savedState.memoryMb, is(2048L));
      assertThat(savedState.diskGb, is(4));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      ContainerTemplateService.State startState = TestHelper.getContainerTemplateServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(containerTemplateService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(ContainerTemplateService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidValueFieldNames")
    public void testInvalidStartStateInvalidField(String fieldName, Object fieldValue) throws Throwable {
      ContainerTemplateService.State startState = TestHelper.getContainerTemplateServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, fieldValue);
      testHost.startServiceSynchronously(containerTemplateService, startState);
    }

    @DataProvider(name = "InvalidValueFieldNames")
    public Object[][] getInvalidValueFieldNames() {
      return new Object[][]{
          {"cpuCount", new Integer(0)},
          {"cpuCount", new Integer(-10)},
          {"memoryMb", new Long(0)},
          {"memoryMb", new Long(-10)},
          {"diskGb", new Integer(0)},
          {"diskGb", new Integer(-10)}
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private ContainerTemplateService containerTemplateService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      containerTemplateService = new ContainerTemplateService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatch() throws Throwable {
      ContainerTemplateService.State startState = TestHelper.getContainerTemplateServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(containerTemplateService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(new ServiceDocument());

      testHost.sendRequestAndWait(patchOperation);
    }
  }
}
