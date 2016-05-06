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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.DiskType;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
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
import static org.hamcrest.CoreMatchers.containsString;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link DiskService}.
 */
public class DiskServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private DiskService service;
  private DiskService.State testState;

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
      service = new DiskService();
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
          Service.ServiceOption.ON_DEMAND_LOAD,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DiskService();
      host = BasicServiceHost.create(
          null,
          DiskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new DiskService.State();
      testState.name = "disk-name";
      testState.diskType = DiskType.PERSISTENT;
      testState.projectId = UUID.randomUUID().toString();
      testState.flavorId = "flavor-id";
      List<QuotaLineItem> cost = new ArrayList<>();
      cost.add(new QuotaLineItem("persistent-disk.cost", 1.0, QuotaUnit.COUNT));
      testState.state = DiskState.CREATING;
      testState.tags = new HashSet<>(Arrays.asList("value"));
      testState.capacityGb = 3;
      testState.agent = "agent-id";
      List<LocalitySpec> affinityList = new ArrayList<>();
      affinityList.add(new LocalitySpec("affinity-1", "vm"));
      affinityList.add(new LocalitySpec("affinity-2", "vm"));
      testState.affinities = affinityList;

      host.startServiceSynchronously(new DiskServiceFactory(), null);

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
      Operation result = dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      DiskService.State createdState = result.getBody(DiskService.State.class);
      assertThat(createdState.name, is(equalTo(testState.name)));
      assertThat(createdState.diskType, is(equalTo(testState.diskType)));
      assertThat(createdState.projectId, is(equalTo(testState.projectId)));
      assertThat(createdState.flavorId, is(equalTo(testState.flavorId)));
      assertThat(createdState.cost, is(equalTo(testState.cost)));
      assertThat(createdState.state, is(equalTo(testState.state)));
      assertThat(createdState.tags, is(equalTo(testState.tags)));
      assertThat(createdState.capacityGb, is(equalTo(testState.capacityGb)));
      assertThat(createdState.agent, is(equalTo(testState.agent)));
      assertThat(createdState.affinities, is(equalTo(testState.affinities)));

      DiskService.State savedState =
          host.getServiceState(DiskService.State.class, createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(testState.name)));
      assertThat(savedState.projectId, is(equalTo(testState.projectId)));
      assertThat(savedState.flavorId, is(equalTo(testState.flavorId)));
      assertThat(savedState.cost, is(equalTo(testState.cost)));
      assertThat(savedState.state, is(equalTo(testState.state)));
      assertThat(savedState.tags, is(equalTo(testState.tags)));
      assertThat(savedState.capacityGb, is(equalTo(testState.capacityGb)));
      assertThat(savedState.agent, is(equalTo(testState.agent)));
      assertThat(savedState.affinities, is(equalTo(testState.affinities)));
    }

    /**
     * Test that none of the non-primitive state fields are initialized.
     *
     * @throws Throwable
     */
    @Test
    public void testAllStateFieldsAreInitializedToNull() throws Throwable {
      DiskService.State state = new DiskService.State();
      for (Field field : DiskService.State.class.getDeclaredFields()) {
        if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        assertThat(field.getName() + " should be null", field.get(state), nullValue());
      }
    }

    /**
     * Test service start with missing projectId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingProject() throws Throwable {
      testState.projectId = null;
      try {
        dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
        fail("Service start did " +
            "not fail when 'projectId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("projectId cannot be null"));
      }
    }

    /**
     * Test service start with missing Name in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingName() throws Throwable {
      testState.name = null;
      try {
        dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
        fail("Service start did not fail when 'Name' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("name cannot be null"));
      }
    }

    /**
     * Test service start with missing State in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingState() throws Throwable {
      testState.state = null;
      try {
        dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
        fail("Service start did not fail when 'State' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("state cannot be null"));
      }
    }

    /**
     * Test service start with missing diskType in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingDiskType() throws Throwable {
      testState.diskType = null;
      try {
        dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
        fail("Service start did not fail when 'diskType' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("diskType cannot be null"));
      }
    }

    /**
     * Test service start with missing flavorId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingFlavorId() throws Throwable {
      testState.flavorId = null;
      try {
        dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
        fail("Service start did not fail when 'flavorId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("flavorId cannot be null"));
      }
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DiskService();
      host = BasicServiceHost.create(
          null,
          DiskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new DiskService.State();
      testState.projectId = "project-id";
      testState.diskType = DiskType.PERSISTENT;
      testState.name = "disk-name";
      testState.state = DiskState.CREATING;
      testState.capacityGb = 2;
      testState.flavorId = "flavor-id";

      host.startServiceSynchronously(new DiskServiceFactory(), null);
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
      Operation createOperation = dcpRestClient.post(DiskServiceFactory.SELF_LINK, testState);
      assertThat(createOperation.getStatusCode(), is(200));
      DiskService.State createdState = createOperation.getBody(DiskService.State.class);

      DiskService.State patchState = new DiskService.State();
      patchState.state = DiskState.ERROR;

      Operation patchOperation = dcpRestClient.patch(createdState.documentSelfLink, patchState);
      DiskService.State result = patchOperation.getBody(DiskService.State.class);
      assertThat(result.state, is(DiskState.ERROR));

      patchOperation = dcpRestClient.get(createdState.documentSelfLink);
      result = patchOperation.getBody(DiskService.State.class);
      assertThat(result.state, is(DiskState.ERROR));
      assertThat(result.capacityGb, is(2));
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DiskService();
      host = BasicServiceHost.create(
          null,
          DiskServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new DiskService.State();
      testState.projectId = "project-id";
      testState.diskType = DiskType.PERSISTENT;
      testState.name = "disk-name";
      testState.state = DiskState.CREATING;
      testState.capacityGb = 2;
      testState.flavorId = "flavor-id";

      host.startServiceSynchronously(new DiskServiceFactory(), null);
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
          DiskServiceFactory.SELF_LINK,
          testState,
          DiskService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
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
          DiskServiceFactory.SELF_LINK,
          testState,
          DiskService.State.class,
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
          DiskServiceFactory.SELF_LINK,
          testState,
          DiskService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS));
    }
  }

}
