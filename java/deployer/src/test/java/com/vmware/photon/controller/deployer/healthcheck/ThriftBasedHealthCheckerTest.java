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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.AssertJUnit.fail;

/**
 * Implements tests for {@link ThriftBasedHealthChecker}.
 */
public class ThriftBasedHealthCheckerTest {

  @Test(dataProvider = "ContainerTypesSupportingThriftStatus")
  public void testGetStatus(
      ContainersConfig.ContainerType containerType,
      StatusProvider mockStatusProvider,
      StatusType responseStatusType,
      boolean expectedResult)
      throws Throwable {

    ThriftBasedHealthChecker thriftBasedHealthChecker =
        spy(new ThriftBasedHealthChecker(containerType, "1.1.1.1", 1111));

    doReturn(mockStatusProvider).when(thriftBasedHealthChecker).buildStatusProvider();

    Status statusResponse = new Status(responseStatusType);
    when(mockStatusProvider.getStatus()).thenReturn(statusResponse);

    assertThat(thriftBasedHealthChecker.isReady(), is(expectedResult));
  }

  @DataProvider(name = "ContainerTypesSupportingThriftStatus")
  private Object[][] getContainerTypesSupportingThriftStatus() {
    return new Object[][]{
        {ContainersConfig.ContainerType.Deployer, mock(DeployerClient.class), StatusType.READY, true},
        {ContainersConfig.ContainerType.Deployer, mock(DeployerClient.class), StatusType.INITIALIZING, false},
        {ContainersConfig.ContainerType.Deployer, mock(DeployerClient.class), StatusType.UNREACHABLE, false},
        {ContainersConfig.ContainerType.Deployer, mock(DeployerClient.class), StatusType.ERROR, false},

        {ContainersConfig.ContainerType.Housekeeper, mock(HousekeeperClient.class), StatusType.READY, true},
        {ContainersConfig.ContainerType.Housekeeper, mock(HousekeeperClient.class), StatusType.INITIALIZING, false},
        {ContainersConfig.ContainerType.Housekeeper, mock(HousekeeperClient.class), StatusType.UNREACHABLE, false},
        {ContainersConfig.ContainerType.Housekeeper, mock(HousekeeperClient.class), StatusType.ERROR, false},
    };
  }

  @Test(expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "Lightwave does not support thrift health check",
      dataProvider = "ContainerTypesNotSupportingThriftStatus")
  public void testFailureForUnsupportedContainerTypes(ContainersConfig.ContainerType containerType) {
    ThriftBasedHealthChecker thriftBasedHealthChecker =
        spy(new ThriftBasedHealthChecker(ContainersConfig.ContainerType.Lightwave, "1.1.1.1", 1111));
    thriftBasedHealthChecker.isReady();
    fail("Expected to throw");
  }

  @DataProvider(name = "ContainerTypesNotSupportingThriftStatus")
  private Object[][] getContainerTypesNotSupportingThriftStatus() {
    return new Object[][]{
        {ContainersConfig.ContainerType.ManagementApi},
        {ContainersConfig.ContainerType.Zookeeper},
        {ContainersConfig.ContainerType.LoadBalancer},
    };
  }

}
