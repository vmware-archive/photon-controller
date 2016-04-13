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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.api.SecurityGroup;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
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
 * Tests {@link ProjectService}.
 */
public class ProjectServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private ProjectService service;
  private ProjectService.State testState;

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
      service = new ProjectService();
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
      ProjectService.State state = new ProjectService.State();
      for (Field field : ProjectService.State.class.getDeclaredFields()) {
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
      service = new ProjectService();
      host = BasicServiceHost.create(
          null,
          ProjectServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ProjectService.State();
      testState.name = "project-name";
      testState.tenantId = UUID.randomUUID().toString();
      testState.resourceTicketId = "resource-ticket-id";
      testState.tagIds = new HashSet<>(Arrays.asList("value"));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new ProjectServiceFactory(), null);

      Operation result = dcpRestClient.post(ProjectServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      ProjectService.State createdState = result.getBody(ProjectService.State.class);
      assertThat(createdState.name, is(equalTo(testState.name)));
      assertThat(createdState.tenantId, is(equalTo(testState.tenantId)));
      assertThat(createdState.resourceTicketId, is(equalTo(testState.resourceTicketId)));
      assertThat(createdState.tagIds, is(equalTo(testState.tagIds)));

      ProjectService.State savedState =
          host.getServiceState(ProjectService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testState.name)));
      assertThat(savedState.tenantId, is(equalTo(testState.tenantId)));
      assertThat(savedState.resourceTicketId, is(equalTo(testState.resourceTicketId)));
      assertThat(savedState.tagIds, is(equalTo(testState.tagIds)));
    }

    /**
     * Test service start with missing tenantId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingTenantId() throws Throwable {
      ProjectService.State startState = new ProjectService.State();
      startState.name = "project-name";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'tenantId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("tenantId cannot be null"));
      }
    }

    /**
     * Test service start with missing Name in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingName() throws Throwable {
      ProjectService.State startState = new ProjectService.State();
      startState.tenantId = "tenant-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'Name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }


  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchtest {

    private String serviceLink;

    @BeforeMethod
    public void setupTest() throws Throwable {
      host = BasicServiceHost.create(
          null,
          ProjectServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      ProjectService.State startState = new ProjectService.State();
      startState.name = "project-name";
      startState.tenantId = UUID.randomUUID().toString();
      startState.resourceTicketId = "resource-ticket-id";
      startState.tagIds = new HashSet<>(Arrays.asList("value"));

      host.startServiceSynchronously(new ProjectServiceFactory(), null);

      Operation result = dcpRestClient.post(ProjectServiceFactory.SELF_LINK, startState);
      assertThat(result.getStatusCode(), is(200));

      serviceLink = result.getBody(ProjectService.State.class).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != host) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    @Test
    public void testPatchStateSuccess() throws Throwable {
      Operation result = dcpRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      ProjectService.State currState = result.getBody(ProjectService.State.class);
      assertThat(currState.securityGroups, nullValue());

      ProjectService.State patchState = new ProjectService.State();
      patchState.securityGroups = new ArrayList<>();
      patchState.securityGroups.add(new SecurityGroup("adminGroup1", true));
      patchState.securityGroups.add(new SecurityGroup("adminGroup2", false));

      result = dcpRestClient.patch(serviceLink, patchState);
      assertThat(result.getStatusCode(), is(200));

      result = dcpRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      ProjectService.State stateAfterPatch = result.getBody(ProjectService.State.class);
      assertThat(ListUtils.isEqualList(stateAfterPatch.securityGroups, patchState.securityGroups), is(true));
    }

    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = ".*name is immutable.*")
    public void testIllegalPatch() throws Throwable {
      Operation result = dcpRestClient.get(serviceLink);
      assertThat(result.getStatusCode(), is(200));

      ProjectService.State currState = result.getBody(ProjectService.State.class);
      assertThat(currState.securityGroups, nullValue());

      ProjectService.State patchState = new ProjectService.State();
      patchState.securityGroups = new ArrayList<>();
      patchState.securityGroups.add(new SecurityGroup("adminGroup1", true));
      patchState.securityGroups.add(new SecurityGroup("adminGroup2", false));
      patchState.name = "cannot change the name";

      dcpRestClient.patch(serviceLink, patchState);
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ProjectService();
      host = BasicServiceHost.create(
          null,
          ProjectServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ProjectService.State();
      testState.name = UUID.randomUUID().toString();
      testState.tenantId = UUID.randomUUID().toString();
      testState.resourceTicketId = "resource-ticket-id";
      testState.tagIds = new HashSet<>(Arrays.asList("value"));

      host.startServiceSynchronously(new ProjectServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      TestHelper.testExpirationOnDelete(
          dcpRestClient,
          host,
          ProjectServiceFactory.SELF_LINK,
          testState,
          ProjectService.State.class,
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
          dcpRestClient,
          host,
          ProjectServiceFactory.SELF_LINK,
          testState,
          ProjectService.State.class,
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
          dcpRestClient,
          host,
          ProjectServiceFactory.SELF_LINK,
          testState,
          ProjectService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
