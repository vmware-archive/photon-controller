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

import com.vmware.photon.controller.api.model.Vm;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link EntityLockService}.
 */
public class EntityLockServiceTest {

  private XenonRestClient xenonRestClient;
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
      xenonRestClient =
          new XenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1), host);
      xenonRestClient.start();

      testState = new EntityLockService.State();
      testState.entityId = UUID.randomUUID().toString();
      testState.entityKind = Vm.KIND;
      testState.ownerTaskId = UUID.randomUUID().toString();
      testState.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;
      testState.documentSelfLink = testState.entityId;

      host.startServiceSynchronously(new EntityLockServiceFactory(), null);
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
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.ownerTaskId, is(equalTo(testState.ownerTaskId)));
      assertThat(createdState.lockOperation, is(nullValue()));
      EntityLockService.State savedState =
          host.getServiceState(EntityLockService.State.class, createdState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.ownerTaskId, is(equalTo(testState.ownerTaskId)));
      assertThat(savedState.lockOperation, is(nullValue()));
    }

    /**
     * Test service start with missing entityId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingEntityId() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.ownerTaskId = "owner-id";
      startState.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'entityId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("entityId cannot be blank"));
      }
    }

    /**
     * Test service start with missing ownerTaskId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingOwnerTaskId() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.entityId = "entity-id";
      startState.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'ownerTaskId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("ownerTaskId cannot be blank"));
      }
    }

    /**
     * Test service start with missing isAvailable in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingLockOperationType() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.entityId = "entity-id";
      startState.ownerTaskId = "owner-id";

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'lockOperation' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("lockOperation cannot be null"));
      }
    }

    /**
     * Test service start with invalid availability flag in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidOperationTypeFlag() throws Throwable {
      EntityLockService.State startState = new EntityLockService.State();
      startState.entityId = "entity-id";
      startState.ownerTaskId = "owner-id";
      startState.documentSelfLink = startState.entityId;
      startState.lockOperation = EntityLockService.State.LockOperation.RELEASE;

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, startState);
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'lockOperation' was RELEASE");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Creating a lock with lockOperation!=ACQUIRE is not allowed"));
      }

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, startState);
        fail("Service start did not fail when 'lockOperation' was RELEASE");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("Creating a lock with lockOperation!=ACQUIRE is not allowed"));
      }
    }

    /**
     * Test releasing a lock multiple times.
     *
     * @throws Throwable
     */
    @Test
    public void testReleaseLockSuccess() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);

      testState.documentSelfLink = createdState.documentSelfLink;
      testState.lockOperation = EntityLockService.State.LockOperation.RELEASE;
      result = xenonRestClient.put(testState.documentSelfLink, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.ownerTaskId, is(nullValue()));
      assertThat(createdState.lockOperation, is(nullValue()));
      EntityLockService.State savedState = host.getServiceState(EntityLockService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.ownerTaskId, is(nullValue()));
      assertThat(savedState.lockOperation, is(nullValue()));

      result = xenonRestClient.put(testState.documentSelfLink, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.ownerTaskId, is(equalTo(testState.ownerTaskId)));
      // The XenonServiceHost returns a null value of the operation for a no-op instead
      // of returning the sent operation done by the NettyServiceClient.
      assertThat(createdState.lockOperation, is(nullValue()));
      savedState = host.getServiceState(EntityLockService.State.class,
          testState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.ownerTaskId, is(nullValue()));
      assertThat(savedState.lockOperation, is(nullValue()));
    }

    /**
     * Test that only the current owner should be able to release a lock.
     *
     * @throws Throwable
     */
    @Test
    public void testReleaseLockFailure() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      testState.documentSelfLink = createdState.documentSelfLink;
      testState.lockOperation = EntityLockService.State.LockOperation.RELEASE;
      testState.ownerTaskId = UUID.randomUUID().toString();

      try {
        xenonRestClient.put(testState.documentSelfLink, testState);
        fail("Only the current owner should be able to release a lock");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("Only the current owner can release a lock"));
      }
    }

    /**
     * Test that the current owner should be able to re-acquire an existing lock.
     * And then release it.
     *
     * @throws Throwable
     */
    @Test
    public void testAcquireLockSuccessByExistingOwner() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.lockOperation, is(nullValue()));
      assertThat(createdState.ownerTaskId, is(testState.ownerTaskId));
      assertThat(createdState.entitySelfLink, is(notNullValue()));
      testState.documentSelfLink = createdState.documentSelfLink;
      EntityLockService.State savedState = host.getServiceState(EntityLockService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.lockOperation, is(nullValue()));
      assertThat(savedState.documentSelfLink, is(notNullValue()));
      assertThat(savedState.ownerTaskId, is(testState.ownerTaskId));
      assertThat(savedState.entitySelfLink, is(createdState.entitySelfLink));

      result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.lockOperation, is(nullValue()));
      assertThat(createdState.ownerTaskId, is(testState.ownerTaskId));
      savedState = host.getServiceState(EntityLockService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.lockOperation, is(nullValue()));
      assertThat(savedState.ownerTaskId, is(testState.ownerTaskId));
      assertThat(savedState.entitySelfLink, is(notNullValue()));

      testState.lockOperation = EntityLockService.State.LockOperation.RELEASE;

      result = xenonRestClient.put(testState.documentSelfLink, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.lockOperation, is(nullValue()));
      assertThat(createdState.ownerTaskId, is(nullValue()));
      savedState = host.getServiceState(EntityLockService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.lockOperation, is(nullValue()));
      assertThat(savedState.ownerTaskId, is(nullValue()));
      assertThat(savedState.entitySelfLink, is(createdState.entitySelfLink));
    }

    /**
     * Test that a new owner should be able to acquire an existing lock if it is available.
     *
     * @throws Throwable
     */
    @Test
    public void testAcquireLockSuccessByNewOwner() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      testState.documentSelfLink = createdState.documentSelfLink;
      testState.lockOperation = EntityLockService.State.LockOperation.RELEASE;

      result = xenonRestClient.put(testState.documentSelfLink, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.ownerTaskId, is(nullValue()));
      assertThat(createdState.lockOperation, is(nullValue()));
      EntityLockService.State savedState = host.getServiceState(EntityLockService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.ownerTaskId, is(nullValue()));
      assertThat(savedState.lockOperation, is(nullValue()));
      assertThat(savedState.entitySelfLink, is(createdState.entitySelfLink));

      testState.ownerTaskId = UUID.randomUUID().toString();
      testState.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;
      result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      createdState = result.getBody(EntityLockService.State.class);
      assertThat(createdState.entityId, is(equalTo(testState.entityId)));
      assertThat(createdState.ownerTaskId, is(equalTo(testState.ownerTaskId)));
      assertThat(createdState.lockOperation, is(nullValue()));
      savedState = host.getServiceState(EntityLockService.State.class,
          testState.documentSelfLink);
      assertThat(savedState.entityId, is(equalTo(testState.entityId)));
      assertThat(savedState.ownerTaskId, is(equalTo(testState.ownerTaskId)));
      assertThat(savedState.lockOperation, is(nullValue()));
      assertThat(savedState.entitySelfLink, is(createdState.entitySelfLink));
    }

    /**
     * Test that entity id cannot be changed.
     *
     * @throws Throwable
     */
    @Test
    public void testEntityIdChangeFailure() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      testState.documentSelfLink = createdState.documentSelfLink;

      testState.entityId = UUID.randomUUID().toString();

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
        fail("Should not be able to change entityId for a lock");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("entityId for a lock cannot be changed"));
      }
    }

    /**
     * Test that entity kind cannot be changed.
     *
     * @throws Throwable
     */
    @Test
    public void testEntityKindChangeFailure() throws Throwable {
      Operation result = xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      EntityLockService.State createdState = result.getBody(EntityLockService.State.class);
      testState.documentSelfLink = createdState.documentSelfLink;

      testState.entityKind = "new-entity-kind";

      try {
        xenonRestClient.post(EntityLockServiceFactory.SELF_LINK, testState);
        fail("Should not be able to change entityId for a lock");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString("entityKind for a lock cannot be changed"));
      }
    }
  }
}
