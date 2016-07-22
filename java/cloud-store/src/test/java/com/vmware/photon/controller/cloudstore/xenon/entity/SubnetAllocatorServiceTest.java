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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link SubnetAllocatorService}.
 */
public class SubnetAllocatorServiceTest {
  private static BasicServiceHost host;
  private static XenonRestClient xenonClient;

  @BeforeSuite
  public void beforeSuite() throws Throwable {
    host = BasicServiceHost.create();
    ServiceHostUtils.startFactoryServices(host, CloudStoreServiceGroup.FACTORY_SERVICES_MAP);

    StaticServerSet serverSet = new StaticServerSet(new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    xenonClient =
        new XenonRestClient(serverSet, Executors.newFixedThreadPool(128), Executors.newScheduledThreadPool(1));
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
      SubnetAllocatorService.State startState = createInitialState();

      Operation result = xenonClient.post(SubnetAllocatorService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      SubnetAllocatorService.State createdState = result.getBody(SubnetAllocatorService.State.class);
      assertThat(ServiceUtils.documentEquals(SubnetAllocatorService.State.class, startState, createdState), is(true));

      SubnetAllocatorService.State savedState = host.getServiceState(SubnetAllocatorService.State.class,
          createdState.documentSelfLink);
      assertThat(ServiceUtils.documentEquals(SubnetAllocatorService.State.class, startState, savedState), is(true));

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == 1,
          host,
          null);

      ServiceDocumentQueryResult queryResult = ServiceHostUtils.getServiceState(host, ServiceDocumentQueryResult.class,
          DhcpSubnetService.FACTORY_LINK,
          "test-host");
      assertThat(queryResult, is(notNullValue()));
      assertThat(queryResult.documentCount, is(1L));
      assertThat(queryResult.documentLinks, is(notNullValue()));
      assertThat(queryResult.documentLinks.size(), is(1));

      DhcpSubnetService.State dhcpSubnetServiceState = ServiceHostUtils.getServiceState(host,
          DhcpSubnetService.State.class, queryResult.documentLinks.get(0), "test-host");
      assertThat(dhcpSubnetServiceState, is(notNullValue()));

      SubnetUtils subnetUtils = new SubnetUtils(startState.rootCidr);
      SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
      assertThat(IpHelper.longToIp(dhcpSubnetServiceState.lowIp),
          is(InetAddresses.forString(subnetInfo.getLowAddress())));
      assertThat(IpHelper.longToIp(dhcpSubnetServiceState.highIp),
          is(InetAddresses.forString(subnetInfo.getHighAddress())));
    }
  }

  /**
   * Tests for handleDelete method.
   */
  public class HandleDeleteTest {
    private SubnetAllocatorService.State startState;

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
      startState = createInitialState();
      Operation result = xenonClient.post(SubnetAllocatorService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));
      startState = result.getBody(SubnetAllocatorService.State.class);
    }

    @AfterMethod
    public void afterMethod() throws Throwable {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }

    @Test
    public void testExtractSubnet() throws Throwable {
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

      DhcpSubnetService.State currentState = host.getServiceState(DhcpSubnetService.State.class,
          DhcpSubnetService.FACTORY_LINK + "/" + subnetId);

      assertThat(currentState.lowIp, is(0L));
      assertThat(currentState.highIp, is(16L));
    }
  }

  private static SubnetAllocatorService.State createInitialState() {
    SubnetAllocatorService.State startState = new SubnetAllocatorService.State();
    startState.rootCidr = "192.168.0.0/16";
    return startState;
  }
}
