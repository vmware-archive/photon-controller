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

import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link VmService}.
 */
public class VmServiceTest {

  private XenonRestClient xenonRestClient;
  private BasicServiceHost host;
  private VmService service;
  private VmService.State testState;

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
      service = new VmService();
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

    /**
     * Test that none of the non-primitive state fields are initialized.
     *
     * @throws Throwable
     */
    @Test
    public void testAllStateFieldsAreInitializedToNull() throws Throwable {
      VmService.State state = new VmService.State();
      for (Field field : VmService.State.class.getDeclaredFields()) {
        if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        assertThat(field.getName() + " should be null", field.get(state), nullValue());
      }
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new VmService();
      host = BasicServiceHost.create(
          null,
          VmServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      xenonRestClient.start();

      testState = new VmService.State();
      testState.name = UUID.randomUUID().toString();
      testState.flavorId = UUID.randomUUID().toString();
      testState.imageId = UUID.randomUUID().toString();
      testState.projectId = UUID.randomUUID().toString();
      testState.vmState = VmState.CREATING;

      host.startServiceSynchronously(new VmServiceFactory(), null);
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
      Operation result = xenonRestClient.post(VmServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      VmService.State createdState = result.getBody(VmService.State.class);
      assertThat(createdState.name, is(equalTo(testState.name)));
      VmService.State savedState = host.getServiceState(VmService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testState.name)));
    }

    @Test
    public void testMissingName() throws Throwable {
      VmService.State startState = new VmService.State();
      startState.flavorId = UUID.randomUUID().toString();
      startState.projectId = UUID.randomUUID().toString();
      startState.imageId = UUID.randomUUID().toString();
      startState.vmState = VmState.CREATING;

      try {
        xenonRestClient.post(VmServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("name cannot be null"));
      }
    }

    @Test
    public void testMissingFlavorId() throws Throwable {
      VmService.State startState = new VmService.State();
      startState.name = UUID.randomUUID().toString();
      startState.projectId = UUID.randomUUID().toString();
      startState.imageId = UUID.randomUUID().toString();
      startState.vmState = VmState.CREATING;

      try {
        xenonRestClient.post(VmServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'flavorId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("flavorId cannot be null"));
      }
    }

    @Test
    public void testMissingProjectId() throws Throwable {
      VmService.State startState = new VmService.State();
      startState.name = UUID.randomUUID().toString();
      startState.flavorId = UUID.randomUUID().toString();
      startState.imageId = UUID.randomUUID().toString();
      startState.vmState = VmState.CREATING;

      try {
        xenonRestClient.post(VmServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'projectId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("projectId cannot be null"));
      }
    }

    @Test
    public void testMissingVmState() throws Throwable {
      VmService.State startState = new VmService.State();
      startState.name = UUID.randomUUID().toString();
      startState.flavorId = UUID.randomUUID().toString();
      startState.projectId = UUID.randomUUID().toString();
      startState.imageId = UUID.randomUUID().toString();

      try {
        xenonRestClient.post(VmServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'vmState' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("vmState cannot be null"));
      }
    }

    @Test
    public void testMissingImageId() throws Throwable {
      VmService.State startState = new VmService.State();
      startState.name = UUID.randomUUID().toString();
      startState.flavorId = UUID.randomUUID().toString();
      startState.projectId = UUID.randomUUID().toString();
      startState.vmState = VmState.CREATING;

      try {
        xenonRestClient.post(VmServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'imageId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("imageId cannot be null"));
      }
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private VmService.State createdState;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new VmService();
      host = BasicServiceHost.create(
          null,
          VmServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      xenonRestClient.start();

      testState = new VmService.State();
      testState.name = UUID.randomUUID().toString();
      testState.flavorId = UUID.randomUUID().toString();
      testState.projectId = UUID.randomUUID().toString();
      testState.imageId = UUID.randomUUID().toString();
      testState.vmState = VmState.CREATING;

      host.startServiceSynchronously(new VmServiceFactory(), null);

      Operation result = xenonRestClient.post(VmServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));
      createdState = result.getBody(VmService.State.class);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation which should always succeed.
     *
     * @throws Throwable
     */
    @Test
    public void testPatchSuccess() throws Throwable {
      VmService.State patchState = new VmService.State();
      patchState.vmState = VmState.STARTED;

      xenonRestClient.patch(createdState.documentSelfLink, patchState);

      Operation found = xenonRestClient.get(createdState.documentSelfLink);
      VmService.State patchedState = found.getBody(VmService.State.class);
      assertThat(patchedState.vmState, is(patchState.vmState));
    }
  }


  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new VmService();
      host = BasicServiceHost.create(
          null,
          VmServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      xenonRestClient.start();

      testState = new VmService.State();
      testState.name = UUID.randomUUID().toString();
      testState.flavorId = UUID.randomUUID().toString();
      testState.projectId = UUID.randomUUID().toString();
      testState.imageId = UUID.randomUUID().toString();
      testState.vmState = VmState.CREATING;

      host.startServiceSynchronously(new VmServiceFactory(), null);
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
          VmServiceFactory.SELF_LINK,
          testState,
          VmService.State.class,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          0L,
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
          VmServiceFactory.SELF_LINK,
          testState,
          VmService.State.class,
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
          VmServiceFactory.SELF_LINK,
          testState,
          VmService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }
}
