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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.StatsStoreType;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link DeploymentService} class.
 */
public class DeploymentServiceTest {

  public DeploymentService.State buildServiceStartState() {
    DeploymentService.State startState = new DeploymentService.State();
    startState.imageDataStoreNames = Collections.singleton("datastore1");
    startState.imageDataStoreUsedForVMs = true;
    startState.state = DeploymentState.CREATING;
    startState.statsEnabled = true;
    return startState;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the DeploymentService constructor.
   */
  public class ConstructorTest {

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);

      DeploymentService deploymentService = new DeploymentService();
      assertThat(deploymentService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private DeploymentService deploymentService;
    private BasicServiceHost testHost;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deploymentService = new DeploymentService();
      testHost = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT,
          null, BasicServiceHost.SERVICE_URI, 10, 10);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testHost != null) {
        BasicServiceHost.destroy(testHost);
      }
    }

    @Test
    public void testStartState() throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(deploymentService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State savedState = testHost.getServiceState(DeploymentService.State.class);
      assertThat(savedState.imageDataStoreNames, is(Collections.singleton("datastore1")));
      assertThat(savedState.imageDataStoreUsedForVMs, is(true));
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(deploymentService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return new Object[][]{
          {"imageDataStoreNames"},
          {"imageDataStoreUsedForVMs"},
          {"state"},
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private DeploymentService deploymentService;
    private BasicServiceHost testHost;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deploymentService = new DeploymentService();
      testHost = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT,
          null, BasicServiceHost.SERVICE_URI, 10, 10);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testHost != null) {
        BasicServiceHost.destroy(testHost);
      }
    }

    @Test
    public void testPatchStateSuccess() throws Throwable {
      final String lotusLoginEndpoint = "https://lotus";
      final String lotusLogoutEndpoint = "https://lotusLogout";
      final String statsStoreEndpoint = "https://stats";
      final Integer statsStorePort = 2000;
      final StatsStoreType statsStoreType = StatsStoreType.GRAPHITE;

      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(deploymentService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.oAuthSwaggerLoginEndpoint = lotusLoginEndpoint;
      patchState.oAuthSwaggerLogoutEndpoint = lotusLogoutEndpoint;
      patchState.oAuthMgmtUiLoginEndpoint = lotusLoginEndpoint;
      patchState.oAuthMgmtUiLogoutEndpoint = lotusLogoutEndpoint;

      patchState.statsStoreEndpoint = statsStoreEndpoint;
      patchState.statsStorePort = statsStorePort;
      patchState.statsStoreType = statsStoreType;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);

      DeploymentService.State currentState = testHost.getServiceState(DeploymentService.State.class);
      assertThat(currentState.oAuthSwaggerLoginEndpoint, is(lotusLoginEndpoint));
      assertThat(currentState.oAuthSwaggerLogoutEndpoint, is(lotusLogoutEndpoint));
      assertThat(currentState.oAuthMgmtUiLoginEndpoint, is(lotusLoginEndpoint));
      assertThat(currentState.oAuthMgmtUiLogoutEndpoint, is(lotusLogoutEndpoint));

      assertTrue(currentState.statsEnabled);
      assertThat(currentState.statsStoreEndpoint, is(statsStoreEndpoint));
      assertThat(currentState.statsStorePort, is(statsStorePort));
      assertThat(currentState.statsStoreType, is(statsStoreType));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testIllegalPatch() throws Throwable {
      final String lightwaveAdminUsername = "NonAdministrator";
      final String lightwaveAdminPassword = "SomePassword22";
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testHost.startServiceSynchronously(deploymentService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.oAuthUserName = lightwaveAdminUsername;
      patchState.oAuthPassword = lightwaveAdminPassword;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, BasicServiceHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {

    private XenonRestClient dcpRestClient;
    private BasicServiceHost host;
    private DeploymentService service;
    private DeploymentService.State testState;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DeploymentService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          DeploymentServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = buildServiceStartState();

      host.startServiceSynchronously(new DeploymentServiceFactory(), null);
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
          DeploymentServiceFactory.SELF_LINK,
          testState,
          DeploymentService.State.class,
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
          DeploymentServiceFactory.SELF_LINK,
          testState,
          DeploymentService.State.class,
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
          DeploymentServiceFactory.SELF_LINK,
          testState,
          DeploymentService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
