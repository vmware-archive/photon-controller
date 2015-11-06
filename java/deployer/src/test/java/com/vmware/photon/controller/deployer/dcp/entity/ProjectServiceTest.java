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

package com.vmware.photon.controller.deployer.dcp.entity;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

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

/**
 * This class implements tests for the {@link ProjectService} class.
 */
public class ProjectServiceTest {

  private ProjectService projectService;
  private TestHost testHost;

  private ProjectService.State buildValidStartState() {
    ProjectService.State startState = new ProjectService.State();
    startState.projectName = "PROJECT_NAME";
    startState.resourceTicketServiceLink = "RESOURCE_TICKET_SERVICE_LINK";
    startState.projectId = "PROJECT_ID";
    startState.documentSelfLink = "/PROJECT_ID";
    return startState;
  }

  /**
   * Dummy test case to force IntelliJ to recognize this class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    @BeforeMethod
    public void setUpTest() {
      projectService = new ProjectService();
    }

    @AfterMethod
    public void tearDownTest() {
      projectService = null;
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(projectService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      projectService = new ProjectService();
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
      ProjectService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(
          projectService, startState, startState.documentSelfLink);
      assertThat(startOperation.getStatusCode(), is(200));

      ProjectService.State savedState = testHost.getServiceState(
          ProjectService.State.class, startState.documentSelfLink);

      assertThat(savedState.projectName, is("PROJECT_NAME"));
      assertThat(savedState.resourceTicketServiceLink, is("RESOURCE_TICKET_SERVICE_LINK"));
    }

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      ProjectService.State startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(projectService, startState, startState.documentSelfLink);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProjectService.State.class,
              NotNull.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      projectService = new ProjectService();
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testPatch() throws Throwable {
      ProjectService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(
          projectService, startState, startState.documentSelfLink);
      assertThat(startOperation.getStatusCode(), is(200));

      ProjectService.State projectPatchState = new ProjectService.State();
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, startState.documentSelfLink, null))
          .setBody(projectPatchState);

      testHost.sendRequestAndWait(patchOperation);
    }
  }
}
