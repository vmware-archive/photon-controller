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

import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link EntityLockService}.
 */
public class EntityLockServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private EntityLockService service;
  private EntityLockService.State testState;

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
      service = new EntityLockService();
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
      service = new EntityLockService();
      host = BasicServiceHost.create(
          null,
          EntityLockServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new EntityLockService.State();
      testState.entityId = UUID.randomUUID().toString();
      testState.taskId = UUID.randomUUID().toString();
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
      host.startServiceSynchronously(new EntityLockServiceFactory(), null);

      Operation result = dcpRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.taskId, is(equalTo(testState.taskId)));
      EntityLockService.State savedState =
          host.getServiceState(EntityLockService.State.class, createdState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.taskId, is(equalTo(testState.taskId)));
    }

    /**
     * Test service start with missing taskId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingEntityId() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.entityId = "entity-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'taskId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("taskId cannot be null"));
      }
    }

    /**
     * Test service start with missing entityId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingTaskId() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.taskId = "task-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'entityId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("entityId cannot be null"));
      }
    }
  }

}
