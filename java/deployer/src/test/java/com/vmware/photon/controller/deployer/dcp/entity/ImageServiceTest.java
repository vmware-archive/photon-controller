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
import java.util.List;

/**
 * This class implements tests for the {@link ImageService} class.
 */
public class ImageServiceTest {

  private ImageService imageService;
  private TestHost testHost;

  private ImageService.State buildValidState() {
    ImageService.State state = new ImageService.State();
    state.imageFile = "imageFile";
    state.imageName = "imageName";

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the ImageService constructor.
   */
  public class ConstructorTest {

    @BeforeMethod
    public void setUpTest() {
      imageService = new ImageService();
    }

    @AfterMethod
    public void tearDownTest() {
      imageService = null;
    }

    /**
     * This test verifies that the service options of a service instance are
     * the expected set of service options.
     */
    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(imageService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements test for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      imageService = new ImageService();
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

    /**
     * This test verifies that a service instance can be created using the
     * default valid startup state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {

      Operation startOperation = testHost.startServiceSynchronously(imageService, buildValidState());
      assertThat(startOperation.getStatusCode(), is(200));

      ImageService.State savedState = testHost.getServiceState(ImageService.State.class);
      assertThat(savedState.imageFile, is("imageFile"));
      assertThat(savedState.imageName, is("imageName"));
    }

    /**
     * This test verfies that a service instance cannot be started with a
     * start state in which the vmId is null.
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String attributeName) throws Throwable {
      ImageService.State invalidState = buildValidState();
      Field declaredField = invalidState.getClass().getDeclaredField(attributeName);
      declaredField.set(invalidState, null);

      testHost.startServiceSynchronously(imageService, invalidState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(ImageService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testHost = TestHost.create();
      imageService = new ImageService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testHost) {
        TestHost.destroy(testHost);
      }

      imageService = null;
      testHost = null;
    }

    /**
     * This test verifies that patch operations succeeds.
     */
    @Test
    public void testPatch() throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(imageService, buildValidState());
      assertThat(startOperation.getStatusCode(), is(200));

      ImageService.State patchState = new ImageService.State();
      patchState.imageId = "imageId";

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
      ImageService.State savedState = testHost.getServiceState(ImageService.State.class);

      assertThat(savedState.imageId, is("imageId"));
    }
  }
}
