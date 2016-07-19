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
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link DhcpSubnetService}.
 */
public class DhcpSubnetServiceTest {
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
      DhcpSubnetService.State startState = createInitialState();

      Operation result = xenonClient.post(DhcpSubnetService.FACTORY_LINK, startState);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DhcpSubnetService.State createdState = result.getBody(DhcpSubnetService.State.class);
      assertThat(ServiceUtils.documentEquals(DhcpSubnetService.State.class, startState, createdState), is(true));

      DhcpSubnetService.State savedState = host.getServiceState(DhcpSubnetService.State.class,
          createdState.documentSelfLink);
      assertThat(ServiceUtils.documentEquals(DhcpSubnetService.State.class, startState, savedState), is(true));
      assertThat(startState.doGarbageCollection, is(false));

    }
  }

  /**
   * Tests for handleDelete method.
   */
  public class HandleDeleteTest {
    private DhcpSubnetService.State startState;

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

  private static DhcpSubnetService.State createInitialState() {
    DhcpSubnetService.State startState = new DhcpSubnetService.State();
    startState.cidr = "192.168.0.0/16";
    return startState;
  }
}
