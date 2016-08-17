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

import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link DhcpSubnetService}.
 */
public class DhcpSubnetServiceTest {
  private static BasicServiceHost host;
  private static XenonRestClient xenonClient;

  private static void commonHostAndClientSetup() throws Throwable {
    host = BasicServiceHost.create();
    ServiceHostUtils.startFactoryServices(host, CloudStoreServiceGroup.FACTORY_SERVICES_MAP);

    StaticServerSet serverSet = new StaticServerSet(new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    xenonClient =
        new XenonRestClient(serverSet, Executors.newFixedThreadPool(128), Executors.newScheduledThreadPool(1));
    xenonClient.start();
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }
    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    @BeforeMethod
    public void beforeMethod() throws Throwable {
      commonHostAndClientSetup();
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testSuccessfulCreation() throws Throwable {
      DhcpSubnetService.State startState = createInitialState();

      Operation result = xenonClient.post(DhcpSubnetService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      DhcpSubnetService.State createdState = result.getBody(DhcpSubnetService.State.class);

      assertThat(createdState.lowIp, is(startState.lowIp));
      assertThat(createdState.highIp, is(startState.highIp));
      assertThat(createdState.size, is(0x10000L));
      assertThat(createdState.doGarbageCollection, is(false));

      DhcpSubnetService.State savedState = host.getServiceState(DhcpSubnetService.State.class,
          createdState.documentSelfLink);
      assertThat(savedState.lowIp, is(startState.lowIp));
      assertThat(savedState.highIp, is(startState.highIp));
      assertThat(savedState.size, is(0x10000L));
      assertThat(savedState.doGarbageCollection, is(false));
    }
  }

  /**
   * Tests for handleDelete method.
   */
  public class HandleDeleteTest {
    private DhcpSubnetService.State startState;

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      commonHostAndClientSetup();
      startState = createInitialState();
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testUsingDefaultExpiration() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonClient,
          host,
          DhcpSubnetService.FACTORY_LINK,
          startState,
          DhcpSubnetService.State.class,
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
          DhcpSubnetService.FACTORY_LINK,
          startState,
          DhcpSubnetService.State.class,
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
          DhcpSubnetService.FACTORY_LINK,
          startState,
          DhcpSubnetService.State.class,
          ServiceUtils.computeExpirationTime(TimeUnit.MINUTES.toMicros(1)),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE),
          ServiceUtils.computeExpirationTime(Integer.MAX_VALUE)
      );
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    private DhcpSubnetService.State startState;
    private final String macAddress = "08:00:27:d8:7d:8e";

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      commonHostAndClientSetup();
      startState = createInitialState();
      Operation result = xenonClient.post(DhcpSubnetService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));
      startState = result.getBody(DhcpSubnetService.State.class);
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testAllocateIpToMac() throws Throwable {
      DhcpSubnetService.IpOperationPatch ipOperationPatch =
          new DhcpSubnetService.IpOperationPatch(
              DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
              macAddress, null);
      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(ipOperationPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      host.sendRequestAndWait(patchOperation);

      DhcpSubnetService.State currentState = host.getServiceState(DhcpSubnetService.State.class,
          startState.documentSelfLink);

      assertThat(currentState.version, is(startState.version + 1));
      assertThat(currentState.ipAllocations.length(), is(1));
      assertThat(currentState.ipAllocations.get(0), is(true));
    }

    @Test
    public void testReleaseIpToMac() throws Throwable {
      DhcpSubnetService.IpOperationPatch ipOperationPatch =
          new DhcpSubnetService.IpOperationPatch(
              DhcpSubnetService.IpOperationPatch.Kind.AllocateIpToMac,
              macAddress, null);
      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(ipOperationPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      Operation completedOperation = host.sendRequestAndWait(patchOperation);

      DhcpSubnetService.IpOperationPatch operationResult =
          completedOperation.getBody(DhcpSubnetService.IpOperationPatch.class);

      DhcpSubnetService.State currentState = host.getServiceState(DhcpSubnetService.State.class,
          startState.documentSelfLink);

      assertThat(currentState.version, is(startState.version + 1));
      assertThat(currentState.ipAllocations.length(), is(1));
      assertThat(currentState.ipAllocations.get(0), is(true));

      ipOperationPatch =
          new DhcpSubnetService.IpOperationPatch(
              DhcpSubnetService.IpOperationPatch.Kind.ReleaseIpForMac,
              macAddress, operationResult.ipAddress);
      patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(ipOperationPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      host.sendRequestAndWait(patchOperation);

      currentState = host.getServiceState(DhcpSubnetService.State.class,
          startState.documentSelfLink);

      assertThat(currentState.version, is(startState.version + 2));
      assertThat(currentState.ipAllocations.length(), is(0));
      assertThat(currentState.ipAllocations.get(0), is(false));
    }

  }

  private static DhcpSubnetService.State createInitialState() {
    String cidr = "192.168.0.0/16";
    SubnetUtils subnetUtils = new SubnetUtils(cidr);
    subnetUtils.setInclusiveHostCount(true);
    SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
    Long lowIp = IpHelper.ipStringToLong(subnetInfo.getLowAddress());
    Long highIp = IpHelper.ipStringToLong(subnetInfo.getHighAddress());

    DhcpSubnetService.State startState = new DhcpSubnetService.State();
    startState.lowIp = lowIp;
    startState.highIp = highIp;
    startState.lowIpDynamic = lowIp + 1;
    startState.highIpDynamic = highIp - 1;
    startState.subnetId = UUID.randomUUID().toString();
    startState.cidr = cidr;

    return startState;
  }
}
