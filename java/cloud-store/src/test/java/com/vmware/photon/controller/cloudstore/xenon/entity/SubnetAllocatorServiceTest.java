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
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

      assertThat(currentState.cidr, is("192.168.0.0/28"));
      assertThat(IpHelper.longToIpString(currentState.lowIp), is("192.168.0.0"));
      assertThat(IpHelper.longToIpString(currentState.highIp), is("192.168.0.15"));

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

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == 0,
          host,
          null);
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

      assertThat(IpHelper.longToIpString(freeRange.low), is("192.168.0.0"));
      assertThat(IpHelper.longToIpString(freeRange.high), is("192.168.255.255"));

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
        Assert.fail("release of non existent subnet should have failed");
      } catch (BadRequestException ex) {
        assertThat(ex.getMessage(), containsString("Could not find the subnet service with the provided subnetId"));
      }
    }
  }

  /**
   * Tests that verify full lifecycle of subnets.
   */
  public static class EndToEndTest {
    private static SubnetAllocatorService.State startState;

    @BeforeClass
    public void setupClass() throws Throwable {
      commonHostAndClientSetup();
      startState = createInitialState();
      Operation result = xenonClient.post(SubnetAllocatorService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));
      startState = result.getBody(SubnetAllocatorService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test(dataProvider = "SubnetAllocationReleaseRequests")
    public void testSubnetAllocationReleaseRequests(Boolean isAllocationRequest,
                                                    String requestedSubnetId,
                                                    Long requestedSize,
                                                    Integer expectedFreeListCount,
                                                    Integer expectedAllocatedSubnetCount,
                                                    String expectedLowIp,
                                                    String expectedHighIp,
                                                    String expectedCidr
    )
        throws Throwable {

      Operation patchOperation = new Operation()
          .setAction(Service.Action.PATCH)
          .setReferer("test-host")
          .setUri(UriUtils.buildUri(host, startState.documentSelfLink));

      if (isAllocationRequest) {
        SubnetAllocatorService.AllocateSubnet allocateSubnetPatch =
            new SubnetAllocatorService.AllocateSubnet(
                requestedSubnetId, requestedSize, 2L);
        patchOperation.setBody(allocateSubnetPatch);
      } else {
        SubnetAllocatorService.ReleaseSubnet releaseSubnetPatch =
            new SubnetAllocatorService.ReleaseSubnet(requestedSubnetId);
        patchOperation.setBody(releaseSubnetPatch);
      }

      Operation completedOperation = host.sendRequestAndWait(patchOperation);
      assertThat(completedOperation.getStatusCode(), is(Operation.STATUS_CODE_OK));

      SubnetAllocatorService.State allocatorState = host.getServiceState(SubnetAllocatorService.State.class,
          startState.documentSelfLink);

      logger.info("Free List Count=" + allocatorState.freeList.size());
      List<SubnetAllocatorService.IpV4Range> sortedFreeList =
          allocatorState.freeList.stream()
              .sorted((left, right) -> Long.compare(left.low, right.low))
              .collect(Collectors.toList());

      for (SubnetAllocatorService.IpV4Range range : sortedFreeList) {
        logger.info("Range Low= " + IpHelper.longToIpString(range.low)
            + " High= " + IpHelper.longToIpString(range.high));
      }

      assertThat("free list count check failed", allocatorState.freeList.size(), is(expectedFreeListCount));

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == (long) expectedAllocatedSubnetCount,
          host,
          null);

      if (isAllocationRequest) {
        DhcpSubnetService.State currentState = host.getServiceState(DhcpSubnetService.State.class,
            DhcpSubnetService.FACTORY_LINK + "/" + requestedSubnetId);

        assertThat(currentState.cidr, is(expectedCidr));
        assertThat(IpHelper.longToIpString(currentState.lowIp), is(expectedLowIp));
        assertThat(IpHelper.longToIpString(currentState.highIp), is(expectedHighIp));
      }

    }

    @DataProvider(name = "SubnetAllocationReleaseRequests")
    public Object[][] getSubnetAllocationReleaseRequests() {
      return new Object[][]{
          // requested isAllocateRequest, subnet id, subnet size
          // expected: free list count, subnet count, start ip, end ip, subnet CIDR
          {true, "subnet1", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet2", 16L, 1, 2, "192.168.0.16", "192.168.0.31", "192.168.0.16/28"},
          {false, "subnet1", null, 2, 1, null, null, null},
          {false, "subnet2", null, 1, 0, null, null, null},
          {true, "subnet3", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet4", 16L, 1, 2, "192.168.0.16", "192.168.0.31", "192.168.0.16/28"},
          {false, "subnet4", null, 1, 1, null, null, null},
          {false, "subnet3", null, 1, 0, null, null, null},
          {true, "subnet5", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet6", 16L, 1, 2, "192.168.0.16", "192.168.0.31", "192.168.0.16/28"},
          {true, "subnet7", 16L, 1, 3, "192.168.0.32", "192.168.0.47", "192.168.0.32/28"},
          {false, "subnet5", null, 2, 2, null, null, null},
          {false, "subnet6", null, 2, 1, null, null, null},
          {false, "subnet7", null, 1, 0, null, null, null},
          {true, "subnet8", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet9", 16L, 1, 2, "192.168.0.16", "192.168.0.31", "192.168.0.16/28"},
          {true, "subnet10", 16L, 1, 3, "192.168.0.32", "192.168.0.47", "192.168.0.32/28"},
          {true, "subnet11", 16L, 1, 4, "192.168.0.48", "192.168.0.63", "192.168.0.48/28"},
          {false, "subnet8", null, 2, 3, null, null, null},
          {false, "subnet10", null, 3, 2, null, null, null},
          {false, "subnet11", null, 2, 1, null, null, null},
          {false, "subnet9", null, 1, 0, null, null, null},
          {true, "subnet12", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet13", 16L, 1, 2, "192.168.0.16", "192.168.0.31", "192.168.0.16/28"},
          {true, "subnet14", 16L, 1, 3, "192.168.0.32", "192.168.0.47", "192.168.0.32/28"},
          {true, "subnet15", 16L, 1, 4, "192.168.0.48", "192.168.0.63", "192.168.0.48/28"},
          {false, "subnet12", null, 2, 3, null, null, null},
          {false, "subnet14", null, 3, 2, null, null, null},
          {false, "subnet13", null, 2, 1, null, null, null},
          {false, "subnet15", null, 1, 0, null, null, null},
          {true, "subnet16", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet17", 8L, 1, 2, "192.168.0.16", "192.168.0.23", "192.168.0.16/29"},
          {true, "subnet18", 32L, 2, 3, "192.168.0.32", "192.168.0.63", "192.168.0.32/27"},
          {true, "subnet19", 16L, 2, 4, "192.168.0.64", "192.168.0.79", "192.168.0.64/28"},
          {true, "subnet20", 64L, 3, 5, "192.168.0.128", "192.168.0.191", "192.168.0.128/26"},
          {false, "subnet16", null, 4, 4, null, null, null},
          {false, "subnet18", null, 4, 3, null, null, null},
          {false, "subnet19", null, 3, 2, null, null, null},
          {false, "subnet17", null, 2, 1, null, null, null},
          {false, "subnet20", null, 1, 0, null, null, null},
          {true, "subnet21", 16L, 1, 1, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {true, "subnet22", 8L, 1, 2, "192.168.0.16", "192.168.0.23", "192.168.0.16/29"},
          {true, "subnet23", 32L, 2, 3, "192.168.0.32", "192.168.0.63", "192.168.0.32/27"},
          {true, "subnet24", 16L, 2, 4, "192.168.0.64", "192.168.0.79", "192.168.0.64/28"},
          {true, "subnet25", 64L, 3, 5, "192.168.0.128", "192.168.0.191", "192.168.0.128/26"},
          {false, "subnet21", null, 4, 4, null, null, null},
          {true, "subnet26", 16L, 3, 5, "192.168.0.0", "192.168.0.15", "192.168.0.0/28"},
          {false, "subnet23", null, 3, 4, null, null, null},
          {true, "subnet27", 256L, 4, 5, "192.168.1.0", "192.168.1.255", "192.168.1.0/24"},
          {false, "subnet24", null, 3, 4, null, null, null},
          {true, "subnet28", 128L, 3, 5, "192.168.2.0", "192.168.2.127", "192.168.2.0/25"},
          {false, "subnet27", null, 3, 4, null, null, null},
          {true, "subnet29", 128L, 4, 5, "192.168.1.0", "192.168.1.127", "192.168.1.0/25"},
          {false, "subnet22", null, 4, 4, null, null, null},
          {false, "subnet25", null, 3, 3, null, null, null},
          {false, "subnet26", null, 3, 2, null, null, null},
          {false, "subnet28", null, 2, 1, null, null, null},
          {false, "subnet29", null, 1, 0, null, null, null},
      };
    }
  }

  private static SubnetAllocatorService.State createInitialState() {
    SubnetAllocatorService.State startState = new SubnetAllocatorService.State();
    startState.rootCidr = "192.168.0.0/16";
    return startState;
  }
}
