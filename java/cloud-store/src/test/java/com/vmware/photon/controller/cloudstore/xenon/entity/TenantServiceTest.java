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

import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.apache.commons.collections.ListUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TenantService}.
 */
public class TenantServiceTest {

  private XenonRestClient xenonRestClient;
  private BasicServiceHost host;
  private TenantService service;
  private TenantService.State testState;

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
      service = new TenantService();
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
    public void testStateFieldsInitializedToNull() throws Throwable {
      TenantService.State state = new TenantService.State();
      for (Field field : TenantService.State.class.getDeclaredFields()) {
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
      service = new TenantService();
      host = BasicServiceHost.create(
          null,
          TenantServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new TenantService.State();
      testState.name = "tenant-name";
      testState.tagIds = new HashSet<>(Arrays.asList("value1", "value2"));
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
      host.startServiceSynchronously(new TenantServiceFactory(), null);

      Operation result = xenonRestClient.post(TenantServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      TenantService.State createdState = result.getBody(TenantService.State.class);
      assertThat(createdState.name, is(equalTo(testState.name)));
      assertThat(createdState.tagIds, is(equalTo(testState.tagIds)));

      TenantService.State savedState =
          host.getServiceState(TenantService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testState.name)));
      assertThat(savedState.tagIds, is(equalTo(testState.tagIds)));
    }

    /**
     * Test service start with missing Name in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingName() throws Throwable {
      TenantService.State startState = new TenantService.State();

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'Name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }

  }

  /**
   * Tests for handlePatch method.
   */
  public class HandlePatchTest {

    private String serviceLink;

    @BeforeMethod
    public void setupTest() throws Throwable {
      host = BasicServiceHost.create(
          null,
          TenantServiceFactory.SELF_LINK,
          10,
          10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      TenantService.State startState = new TenantService.State();
      startState.name = "tenant-name";
      startState.tagIds = new HashSet<>(Arrays.asList("value1", "value2"));

      host.startServiceSynchronously(new TenantServiceFactory(), null);

      Operation result = xenonRestClient.post(TenantServiceFactory.SELF_LINK, startState);
      assertThat(result.getStatusCode(), is(200));

      serviceLink = result.getBody(TenantService.State.class).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != host) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      xenonRestClient.stop();
    }

    @Test
    public void testPatchStateSuccess() throws Throwable {
      Operation result = xenonRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      TenantService.State currState = result.getBody(TenantService.State.class);
      assertThat(currState.securityGroups, nullValue());

      TenantService.State patchState = new TenantService.State();
      patchState.securityGroups = new ArrayList<>();
      patchState.securityGroups.add(new SecurityGroup("adminGroup1", true));
      patchState.securityGroups.add(new SecurityGroup("adminGroup2", false));

      result = xenonRestClient.patch(serviceLink, patchState);
      assertThat(result.getStatusCode(), is(200));

      result = xenonRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      TenantService.State stateAfterPatch = result.getBody(TenantService.State.class);
      assertThat(ListUtils.isEqualList(stateAfterPatch.securityGroups, patchState.securityGroups),
          is(true));
    }

    @Test
    public void testIllegalPatch() throws Throwable {
      Operation result = xenonRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      TenantService.State currState = result.getBody(TenantService.State.class);
      assertThat(currState.securityGroups, nullValue());

      TenantService.State patchState = new TenantService.State();
      patchState.name = "cannot change the name";
      patchState.securityGroups = new ArrayList<>();
      patchState.securityGroups.add(new SecurityGroup("adminGroup1", true));
      patchState.securityGroups.add(new SecurityGroup("adminGroup2", false));

      try {
        xenonRestClient.patch(serviceLink, patchState);
        fail("Should have failed due to updating immutable field");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("name is immutable"));
      }
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new TenantService();
      host = BasicServiceHost.create(
          null,
          TenantServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new TenantService.State();
      testState.name = UUID.randomUUID().toString();
      testState.tagIds = new HashSet<>(Arrays.asList("value1", "value2"));

      host.startServiceSynchronously(new TenantServiceFactory(), null);
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
          TenantServiceFactory.SELF_LINK,
          testState,
          TenantService.State.class,
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
          TenantServiceFactory.SELF_LINK,
          testState,
          TenantService.State.class,
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
          TenantServiceFactory.SELF_LINK,
          testState,
          TenantService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
