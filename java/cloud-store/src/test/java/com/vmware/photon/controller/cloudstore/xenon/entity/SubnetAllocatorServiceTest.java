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
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
        new XenonRestClient(serverSet, Executors.newFixedThreadPool(128), Executors.newScheduledThreadPool(1), host);
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

      assertThat(createdState.freeList.size(), is(1));
      SubnetAllocatorService.IpV4Range freeRange = createdState.freeList.iterator().next();
      InetAddress lowAddress = IpHelper.longToIp(freeRange.low);
      InetAddress highAddress = IpHelper.longToIp(freeRange.high);
      assertThat(createdState.rootCidr, is("192.168.0.0/16"));
      assertThat(lowAddress.getHostAddress(), is("192.168.0.0"));
      assertThat(highAddress.getHostAddress(), is("192.168.255.255"));

      SubnetAllocatorService.State savedState = host.getServiceState(SubnetAllocatorService.State.class,
          createdState.documentSelfLink);

      assertThat(savedState.freeList.size(), is(1));
      freeRange = savedState.freeList.iterator().next();
      lowAddress = IpHelper.longToIp(freeRange.low);
      highAddress = IpHelper.longToIp(freeRange.high);
      assertThat(savedState.rootCidr, is("192.168.0.0/16"));
      assertThat(lowAddress.getHostAddress(), is("192.168.0.0"));
      assertThat(highAddress.getHostAddress(), is("192.168.255.255"));
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
      Operation completedOperation = host.sendRequestAndWait(patchOperation);

      assertThat(completedOperation.getStatusCode(), is(Operation.STATUS_CODE_OK));

      SubnetAllocatorService.State allocatorState = host.getServiceState(SubnetAllocatorService.State.class,
          startState.documentSelfLink);

      assertThat(allocatorState.freeList.size(), is(1));

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

      assertThat(currentState.cidr, is("192.168.0.0/28"));
      assertThat(lowAddress.getHostAddress(), is("192.168.0.0"));
      assertThat(highAddress.getHostAddress(), is("192.168.0.15"));

    }

    @Test
    public void testAllocateSubnetFailure() throws Throwable {
      String subnetId = UUID.randomUUID().toString();
      SubnetAllocatorService.AllocateSubnet allocateSubnetPatch =
          new SubnetAllocatorService.AllocateSubnet(
              subnetId, (long) Integer.MAX_VALUE, 4L);
      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(allocateSubnetPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      try {
        host.sendRequestAndWait(patchOperation);
        Assert.fail("Allocation of such a large subnet should have failed");
      } catch (BadRequestException ex) {
        assertThat(ex.getMessage(), containsString("Could not find any IP range big enough to allocate"));
      }

      SubnetAllocatorService.State allocatorState = host.getServiceState(SubnetAllocatorService.State.class,
          startState.documentSelfLink);

      assertThat(allocatorState.freeList.size(), is(1));
    }

    @Test
    public void testReleaseSubnetSuccess() throws Throwable {
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

      SubnetAllocatorService.ReleaseSubnet releaseSubnetPatch =
          new SubnetAllocatorService.ReleaseSubnet(subnetId);
      patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(releaseSubnetPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      host.sendRequestAndWait(patchOperation);

      SubnetAllocatorService.State allocatorState = host.getServiceState(SubnetAllocatorService.State.class,
          startState.documentSelfLink);

      assertThat(allocatorState.freeList.size(), is(1));

      SubnetAllocatorService.IpV4Range freeRange = allocatorState.freeList.iterator().next();

      InetAddress lowAddress = IpHelper.longToIp(freeRange.low);
      InetAddress highAddress = IpHelper.longToIp(freeRange.high);
      assertThat(lowAddress.getHostAddress(), is("192.168.0.0"));
      assertThat(highAddress.getHostAddress(), is("192.168.255.255"));

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == 0,
          host,
          null);
    }

    @Test
    public void testReleaseSubnetFailure() throws Throwable {
      String subnetId = UUID.randomUUID().toString();
      SubnetAllocatorService.ReleaseSubnet releaseSubnetPatch =
          new SubnetAllocatorService.ReleaseSubnet(subnetId);
      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setBody(releaseSubnetPatch)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));
      try {
        host.sendRequestAndWait(patchOperation);
        Assert.fail("relase of non existent subnet should have failed");
      } catch (BadRequestException ex) {
        assertThat(ex.getMessage(), containsString("Could not find the subnet service with the provided subnetId"));
      }
    }
  }

  private static SubnetAllocatorService.State createInitialState() {
    SubnetAllocatorService.State startState = new SubnetAllocatorService.State();
    startState.rootCidr = "192.168.0.0/16";
    return startState;
  }
}
