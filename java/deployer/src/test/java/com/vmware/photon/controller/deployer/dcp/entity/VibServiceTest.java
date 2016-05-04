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

import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
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

/**
 * This class implements unit tests for the {@link VibService} class.
 */
public class VibServiceTest {

  /**
   * This dummy test case enables IntelliJ to recognize this class as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private VibService vibService;

    @BeforeMethod
    public void setUpTest() {
      vibService = new VibService();
    }

    @Test
    public void testOptions() {
      assertThat(vibService.getOptions(), is(EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION)));
    }
  }

  /**
   * This class implements tests for the {@link VibService#handleStart(Operation)} method.
   */
  public class HandleStartTest {

    private TestHost testHost;
    private VibService vibService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      vibService = new VibService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test
    public void testValidStartState() throws Throwable {
      VibService.State startState = TestHelper.getVibServiceStartState();
      Operation startOp = testHost.startServiceSynchronously(vibService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      VibService.State serviceState = testHost.getServiceState(VibService.State.class);
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
      assertThat(serviceState.vibName, is("VIB_NAME"));
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      VibService.State startState = TestHelper.getVibServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(vibService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              VibService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link VibService#handlePatch(Operation)} method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private VibService vibService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      vibService = new VibService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test
    public void testValidPatch() throws Throwable {
      VibService.State startState = TestHelper.getVibServiceStartState();
      Operation startOp = testHost.startServiceSynchronously(vibService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      VibService.State patchState = new VibService.State();
      patchState.uploadPath = "UPLOAD_PATH";

      Operation patchOp = testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));

      assertThat(patchOp.getStatusCode(), is(200));

      VibService.State serviceState = testHost.getServiceState(VibService.State.class);
      assertThat(serviceState.uploadPath, is("UPLOAD_PATH"));
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testPatch(String fieldName) throws Throwable {
      VibService.State startState = TestHelper.getVibServiceStartState();
      Operation startOp = testHost.startServiceSynchronously(vibService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      VibService.State patchState = new VibService.State();
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              VibService.State.class, Immutable.class));
    }
  }
}
