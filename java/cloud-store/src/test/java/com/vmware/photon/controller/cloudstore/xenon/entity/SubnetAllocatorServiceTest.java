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
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link SubnetAllocatorService}.
 */
public class SubnetAllocatorServiceTest {

  private static final Logger logger = LoggerFactory.getLogger(SubnetAllocatorServiceTest.class);

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
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
      commonHostAndClientTeardown();
    }

    @Test
    public void testSuccessfulCreation() throws Throwable {
      SubnetAllocatorService.State startState = createInitialState();

      Operation result = xenonClient.post(SubnetAllocatorService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      SubnetAllocatorService.State createdState = result.getBody(SubnetAllocatorService.State.class);

      SubnetUtils subnetUtils = new SubnetUtils(startState.rootCidr);
      SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
      Long lowIp, highIp;

      InetAddress lowIpAddress = InetAddresses.forString(subnetInfo.getLowAddress());
      lowIp = IpHelper.ipToLong((Inet4Address) lowIpAddress);

      InetAddress highIpAddress = InetAddresses.forString(subnetInfo.getHighAddress());
      highIp = IpHelper.ipToLong((Inet4Address) highIpAddress);

      SubnetAllocatorService.IpV4Range ipV4Range = new SubnetAllocatorService.IpV4Range(lowIp, highIp);
      startState.freeList = new ArrayList<>();
      startState.freeList.add(ipV4Range);

      assertThat(ServiceUtils.documentEquals(SubnetAllocatorService.State.class, startState, createdState), is(true));

      SubnetAllocatorService.State savedState = host.getServiceState(SubnetAllocatorService.State.class,
          createdState.documentSelfLink);
      assertThat(ServiceUtils.documentEquals(SubnetAllocatorService.State.class, startState, savedState), is(true));
    }
  }

  /**
   * Tests for handleDelete method.
   */
  public class HandleDeleteTest {
    private SubnetAllocatorService.State startState;

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      commonHostAndClientSetup();
      startState = createInitialState();
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
      commonHostAndClientTeardown();
    }

    @Test
    public void testUsingDefaultExpiration() throws Throwable {
      TestHelper.testExpirationOnDelete(
          xenonClient,
          host,
          SubnetAllocatorService.FACTORY_LINK,
          startState,
          SubnetAllocatorService.State.class,
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
          SubnetAllocatorService.FACTORY_LINK,
          startState,
          SubnetAllocatorService.State.class,
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
          SubnetAllocatorService.FACTORY_LINK,
          startState,
          SubnetAllocatorService.State.class,
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
    private SubnetAllocatorService.State startState;

    @BeforeMethod
    public void beforeMethod() throws Throwable {
      commonHostAndClientSetup();
      startState = createInitialState();
      Operation result = xenonClient.post(SubnetAllocatorService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));
      startState = result.getBody(SubnetAllocatorService.State.class);
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
      commonHostAndClientTeardown();
    }

    @Test
    public void testAllocateSubnet() throws Throwable {
      String subnetId = UUID.randomUUID().toString();
      SubnetAllocatorService.AllocateSubnet allocateSubnetPatch =
          new SubnetAllocatorService.AllocateSubnet(
              subnetId, 16L, 4L);
      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(allocateSubnetPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      host.sendRequestAndWait(patchOperation);

      SubnetAllocatorService.State allocatorState = host.getServiceState(SubnetAllocatorService.State.class,
          startState.documentSelfLink);

      assertThat(allocatorState.freeList.size(), is(2));

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == 1,
          host,
          null);

      DhcpSubnetService.State currentState = host.getServiceState(DhcpSubnetService.State.class,
          DhcpSubnetService.FACTORY_LINK + "/" + subnetId);

      InetAddress lowAddress = IpHelper.longToIp(currentState.lowIp);
      InetAddress highAddress = IpHelper.longToIp(currentState.highIp);

      assertThat(currentState.cidr, is("192.168.0.16/28"));
      assertThat(lowAddress.getHostAddress(), is("192.168.0.16"));
      assertThat(highAddress.getHostAddress(), is("192.168.0.31"));

    }
  }

  private static SubnetAllocatorService.State createInitialState() {
    SubnetAllocatorService.State startState = new SubnetAllocatorService.State();
    startState.rootCidr = "192.168.0.0/16";
    return startState;
  }
}
