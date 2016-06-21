/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService}.
 */
public class VirtualNetworkServiceTest {
  private static BasicServiceHost host;
  private static XenonRestClient xenonClient;

  @BeforeSuite
  public void beforeSuite() throws Throwable {
    host = BasicServiceHost.create();
    ServiceHostUtils.startFactoryServices(host, CloudStoreServiceGroup.FACTORY_SERVICES_MAP);

    StaticServerSet serverSet = new StaticServerSet(new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    xenonClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(128));
    xenonClient.start();
  }

  @AfterSuite
  public void afterSuite() throws Throwable {
    xenonClient.stop();
    host.destroy();
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }

    @Test
    public void testSuccessfulCreation() throws Throwable {
      VirtualNetworkService.State startState = createInitialState();

      Operation result = xenonClient.post(VirtualNetworkService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
      assertThat(ServiceUtils.documentEquals(VirtualNetworkService.State.class, startState, createdState), is(true));

      VirtualNetworkService.State savedState = host.getServiceState(VirtualNetworkService.State.class,
          createdState.documentSelfLink);
      assertThat(ServiceUtils.documentEquals(VirtualNetworkService.State.class, startState, savedState), is(true));
    }

    @Test
    public void testMissingVirtualNetworkName() throws Throwable {
      VirtualNetworkService.State startState = createInitialState();
      startState.name = null;

      try {
        xenonClient.post(VirtualNetworkService.FACTORY_LINK, startState);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("name cannot be null"));
      }
    }

    @Test
    public void testMissingRoutingType() throws Throwable {
      VirtualNetworkService.State startState = createInitialState();
      startState.routingType = null;

      try {
        xenonClient.post(VirtualNetworkService.FACTORY_LINK, startState);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("routingType cannot be null"));
      }
    }

    @Test
    public void testMissingNetworkState() throws Throwable {
      VirtualNetworkService.State startState = createInitialState();
      startState.state = null;

      try {
        xenonClient.post(VirtualNetworkService.FACTORY_LINK, startState);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("state cannot be null"));
      }
    }
  }

  /**
   * Tests for handlePatch.
   */
  public static class HandlePatchTest {
    private VirtualNetworkService.State createdState;

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      createdState = createVirtualNetworkService();
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }

    @Test
    public void testSuccessfulPatch() throws Throwable {
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.state = NetworkState.READY;
      patchState.description = "desc";
      patchState.logicalSwitchDownlinkPortIds = new HashMap<>();
      patchState.logicalSwitchDownlinkPortIds.put("vm1", "port1");

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      VirtualNetworkService.State savedState = host.getServiceState(VirtualNetworkService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.state, is(patchState.state));
      assertThat(savedState.description, is(patchState.description));
      assertThat(savedState.name, is(createdState.name));
      assertThat(savedState.routingType, is(createdState.routingType));
      assertThat(savedState.logicalSwitchDownlinkPortIds, equalTo(patchState.logicalSwitchDownlinkPortIds));
    }

    @Test
    public void testFailedToChangeName() throws Throwable {
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.name = "vn2";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("name cannot be set or changed in a patch"));
      }
    }

    @Test
    public void testFailedToChangeRoutingType() throws Throwable {
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.routingType = RoutingType.ISOLATED;

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("routingType cannot be set or changed in a patch"));
      }
    }

    @Test
    public void testFailedToChangePrentId() throws Throwable {
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.parentId = "parentId2";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("parentId is immutable"));
      }
    }

    @Test
    public void testFailedToChangeParentKind() throws Throwable {
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.parentKind = "parentKind2";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to illegal state");
      } catch (Exception e) {
        assertThat(e.getMessage(), is("parentKind is immutable"));
      }
    }
  }

  /**
   * Tests for handleDelete method.
   */
  public class HandleDeleteTest {
    private VirtualNetworkService.State startState;

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      startState = createInitialState();
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }

    @Test
    public void testUsingDefaultExpiration() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonClient,
          host,
          VirtualNetworkService.FACTORY_LINK,
          startState,
          VirtualNetworkService.State.class,
          0L,
          0L,
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS)
      );
    }

    @Test
    public void testUsingExpirationInCurrentState() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonClient,
          host,
          VirtualNetworkService.FACTORY_LINK,
          startState,
          VirtualNetworkService.State.class,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          0L,
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE)
      );
    }

    @Test
    public void testUsingExpirationInDeleteOperation() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonClient,
          host,
          VirtualNetworkService.FACTORY_LINK,
          startState,
          VirtualNetworkService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE)
      );
    }
  }

  private static VirtualNetworkService.State createVirtualNetworkService() throws Throwable {
    VirtualNetworkService.State startState = createInitialState();
    Operation result = xenonClient.post(VirtualNetworkService.FACTORY_LINK, startState);

    return result.getBody(VirtualNetworkService.State.class);
  }

  private static VirtualNetworkService.State createInitialState() {
    VirtualNetworkService.State startState = new VirtualNetworkService.State();
    startState.name = "vn1";
    startState.state = NetworkState.CREATING;
    startState.routingType = RoutingType.ROUTED;
    startState.parentId = "parentId";
    startState.parentKind = "parentKind";

    return startState;
  }
}
