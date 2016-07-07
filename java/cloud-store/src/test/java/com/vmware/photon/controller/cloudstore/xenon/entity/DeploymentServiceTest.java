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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.StatsStoreType;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
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
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deploymentService = new DeploymentService();
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test
    public void testStartState() throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State createdState = startOperation.getBody(DeploymentService.State.class);

      DeploymentService.State savedState = testEnvironment.getServiceState(createdState.documentSelfLink,
          DeploymentService.State.class);
      assertThat(savedState.imageDataStoreNames, is(Collections.singleton("datastore1")));
      assertThat(savedState.imageDataStoreUsedForVMs, is(true));
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
    }

    @Test
    public void testStartWithDhcpConfiguration() throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      startState.dhcpVmConfiguration = new DeploymentService.DhcpVmConfiguration();
      startState.dhcpVmConfiguration.vmImageId = UUID.randomUUID().toString();
      startState.dhcpVmConfiguration.vmFlavorId = UUID.randomUUID().toString();
      startState.dhcpVmConfiguration.vmDiskFlavorId = UUID.randomUUID().toString();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);

      assertThat(startOperation.getStatusCode(), is(200));
      DeploymentService.State createdState = startOperation.getBody(DeploymentService.State.class);

      DeploymentService.State savedState = testEnvironment.getServiceState(createdState.documentSelfLink,
          DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration.vmImageId, is(startState.dhcpVmConfiguration.vmImageId));
      assertThat(savedState.dhcpVmConfiguration.vmFlavorId, is(startState.dhcpVmConfiguration.vmFlavorId));
      assertThat(savedState.dhcpVmConfiguration.vmDiskFlavorId, is(startState.dhcpVmConfiguration.vmDiskFlavorId));
    }

    @Test
    public void testStartWithIncompleteDhcpConfiguration() throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      startState.dhcpVmConfiguration = new DeploymentService.DhcpVmConfiguration();
      try {
        testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
        Assert.fail("Service start should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmImageId should not be blank when dhcpVmConfiguration is not null"));
      }

      deploymentService = new DeploymentService();
      startState.dhcpVmConfiguration.vmImageId = UUID.randomUUID().toString();
      try {
        testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
        Assert.fail("Service start should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmFlavorId should not be blank when dhcpVmConfiguration is not null"));
      }

      deploymentService = new DeploymentService();
      startState.dhcpVmConfiguration.vmFlavorId = UUID.randomUUID().toString();
      try {
        testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
        Assert.fail("Service start should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmDiskFlavorId should not be blank when dhcpVmConfiguration is not null"));
      }

      deploymentService = new DeploymentService();
      startState.dhcpVmConfiguration.vmDiskFlavorId = UUID.randomUUID().toString();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);

      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State createdState = startOperation.getBody(DeploymentService.State.class);

      DeploymentService.State savedState = testEnvironment.getServiceState(createdState.documentSelfLink,
          DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration.vmImageId, is(startState.dhcpVmConfiguration.vmImageId));
      assertThat(savedState.dhcpVmConfiguration.vmFlavorId, is(startState.dhcpVmConfiguration.vmFlavorId));
      assertThat(savedState.dhcpVmConfiguration.vmDiskFlavorId, is(startState.dhcpVmConfiguration.vmDiskFlavorId));
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
    private TestEnvironment testEnvironment;
    private TestEnvironment pauseTestEnv;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deploymentService = new DeploymentService();
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (pauseTestEnv != null) {
        pauseTestEnv.stop();
        pauseTestEnv = null;
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
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State createState = startOperation.getBody(DeploymentService.State.class);

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.oAuthSwaggerLoginEndpoint = lotusLoginEndpoint;
      patchState.oAuthSwaggerLogoutEndpoint = lotusLogoutEndpoint;
      patchState.oAuthMgmtUiLoginEndpoint = lotusLoginEndpoint;
      patchState.oAuthMgmtUiLogoutEndpoint = lotusLogoutEndpoint;

      patchState.statsStoreEndpoint = statsStoreEndpoint;
      patchState.statsStorePort = statsStorePort;
      patchState.statsStoreType = statsStoreType;

      testEnvironment.sendPatchAndWait(createState.documentSelfLink, patchState);

      DeploymentService.State currentState = testEnvironment.getServiceState(createState.documentSelfLink,
          DeploymentService.State.class);
      assertThat(currentState.oAuthSwaggerLoginEndpoint, is(lotusLoginEndpoint));
      assertThat(currentState.oAuthSwaggerLogoutEndpoint, is(lotusLogoutEndpoint));
      assertThat(currentState.oAuthMgmtUiLoginEndpoint, is(lotusLoginEndpoint));
      assertThat(currentState.oAuthMgmtUiLogoutEndpoint, is(lotusLogoutEndpoint));

      assertTrue(currentState.statsEnabled);
      assertThat(currentState.statsStoreEndpoint, is(statsStoreEndpoint));
      assertThat(currentState.statsStorePort, is(statsStorePort));
      assertThat(currentState.statsStoreType, is(statsStoreType));
    }

    @Test
    public void testPauseSystemOnThreeNodes() throws Throwable {
      pauseTestEnv = TestEnvironment.create(3);

      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(pauseTestEnv.getServerSet());
      List<SystemConfig> systemConfigs = new ArrayList<>();
      for (PhotonControllerXenonHost host : pauseTestEnv.getHosts()) {
        host.setCloudStoreHelper(cloudStoreHelper);
        systemConfigs.add(SystemConfig.createInstance(host));
      }
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = pauseTestEnv.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State createState = startOperation.getBody(DeploymentService.State.class);

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.state = DeploymentState.PAUSED;
      pauseTestEnv.sendPatchAndWait(createState.documentSelfLink, patchState);

      DeploymentService.State currentState = pauseTestEnv.getServiceState(createState.documentSelfLink,
          DeploymentService.State.class);
      assertThat(currentState.state, is(DeploymentState.PAUSED));

      for (SystemConfig instance : systemConfigs) {
        assertThat(instance.isPaused(), is(true));
      }
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testIllegalPatch() throws Throwable {
      final String lightwaveAdminUsername = "NonAdministrator";
      final String lightwaveAdminPassword = "SomePassword22";
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.oAuthUserName = lightwaveAdminUsername;
      patchState.oAuthPassword = lightwaveAdminPassword;

      DeploymentService.State createState = startOperation.getBody(DeploymentService.State.class);
      testEnvironment.sendPatchAndWait(createState.documentSelfLink, patchState);
    }

    @Test
    public void testPatchWithDhcpConfiguration() throws Throwable {
      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State savedState = startOperation.getBody(DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration, is(nullValue()));

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.dhcpVmConfiguration = new DeploymentService.DhcpVmConfiguration();
      patchState.dhcpVmConfiguration.vmImageId = UUID.randomUUID().toString();
      patchState.dhcpVmConfiguration.vmFlavorId = UUID.randomUUID().toString();
      patchState.dhcpVmConfiguration.vmDiskFlavorId = UUID.randomUUID().toString();

      Operation patchOperation = testEnvironment.sendPatchAndWait(savedState.documentSelfLink, patchState);

      savedState = patchOperation.getBody(DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration.vmImageId, is(patchState.dhcpVmConfiguration.vmImageId));
      assertThat(savedState.dhcpVmConfiguration.vmFlavorId, is(patchState.dhcpVmConfiguration.vmFlavorId));
      assertThat(savedState.dhcpVmConfiguration.vmDiskFlavorId, is(patchState.dhcpVmConfiguration.vmDiskFlavorId));
    }

    @Test
    public void testPatchWithIncompleteDhcpConfiguration() throws Throwable {

      DeploymentService.State startState = buildServiceStartState();
      Operation startOperation = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentService.State savedState = startOperation.getBody(DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration, is(nullValue()));

      DeploymentService.State patchState = new DeploymentService.State();
      patchState.dhcpVmConfiguration = new DeploymentService.DhcpVmConfiguration();
      try {
        testEnvironment.sendPatchAndWait(savedState.documentSelfLink, patchState);

        Assert.fail("Service patch should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmImageId should not be blank when dhcpVmConfiguration is not null"));
      }

      patchState.dhcpVmConfiguration.vmImageId = UUID.randomUUID().toString();
      try {
        testEnvironment.sendPatchAndWait(savedState.documentSelfLink, patchState);
        Assert.fail("Service patch should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmFlavorId should not be blank when dhcpVmConfiguration is not null"));
      }

      patchState.dhcpVmConfiguration.vmFlavorId = UUID.randomUUID().toString();
      try {
        testEnvironment.sendPatchAndWait(savedState.documentSelfLink, patchState);
        Assert.fail("Service patch should fail with incomplete dhcp configuration");
      } catch (BadRequestException be) {
        assertThat(be.getMessage(),
            containsString("vmDiskFlavorId should not be blank when dhcpVmConfiguration is not null"));
      }

      patchState.dhcpVmConfiguration.vmDiskFlavorId = UUID.randomUUID().toString();
      Operation patchOperation = testEnvironment.sendPatchAndWait(savedState.documentSelfLink, patchState);

      assertThat(patchOperation.getStatusCode(), is(200));

      savedState = patchOperation.getBody(DeploymentService.State.class);
      assertThat(savedState.dhcpVmConfiguration.vmImageId, is(patchState.dhcpVmConfiguration.vmImageId));
      assertThat(savedState.dhcpVmConfiguration.vmFlavorId, is(patchState.dhcpVmConfiguration.vmFlavorId));
      assertThat(savedState.dhcpVmConfiguration.vmDiskFlavorId, is(patchState.dhcpVmConfiguration.vmDiskFlavorId));
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {

    private XenonRestClient xenonRestClient;
    private BasicServiceHost host;
    private DeploymentService service;
    private DeploymentService.State testState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DeploymentService();
      host = BasicServiceHost.create(
          null,
          DeploymentServiceFactory.SELF_LINK,
          10, 10);

      testEnvironment = TestEnvironment.create(1);
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(testEnvironment.getHosts()[0].getPreferredAddress(),
              testEnvironment.getHosts()[0].getPort()));
      xenonRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      xenonRestClient.start();

      testState = buildServiceStartState();
      host.startServiceSynchronously(new DeploymentServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
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
          xenonRestClient,
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
          xenonRestClient,
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
