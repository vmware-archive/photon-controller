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

package com.vmware.photon.controller.core;


import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.xenon.common.ServiceHost;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the DefaultDeployment method.
 */
public class DefaultDeploymentTest {

  private static final long SERVICES_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

  private TestEnvironment testEnvironment;
  private PhotonControllerConfig photonControllereConfig;

  @BeforeMethod
  public void setUp() throws Throwable {

    testEnvironment = TestEnvironment.create(3);

    testEnvironment.startFactoryServiceSynchronously(
        DeploymentServiceFactory.class,
        DeploymentServiceFactory.SELF_LINK);

    photonControllereConfig = ConfigBuilder.build(
        PhotonControllerConfig.class,
        ConfigTest.class.getResource("/config.yml").getPath());
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    if (testEnvironment != null) {
      testEnvironment.stop();
      testEnvironment = null;
    }
  }

  /**
   * Test ONE default deployment can be created with multi host environment.
   *
   * @throws Throwable
   */
  @Test
  public void testDefaultDeploymentGetsCreated() throws Throwable {

    ArrayList<String> peers = new ArrayList<>();
    for (ServiceHost host : testEnvironment.getHosts()) {
      peers.add(host.getUri().toString());
    }

    String defaultDeploymentSelfLink =
        DeploymentServiceFactory.SELF_LINK + "/" + DeployerDefaults.DEFAULT_DEPLOYMENT_ID;

    ServiceHostUtils.waitForServiceAvailability(
        testEnvironment.getHosts()[0], SERVICES_STARTUP_TIMEOUT, DeploymentServiceFactory.SELF_LINK);

    DefaultDeployment.createDefaultDeployment(
        peers.toArray(new String[peers.size()]),
        photonControllereConfig.getDeployerConfig(),
        testEnvironment.getHosts()[0]);

    // Other host might create the default deployment as well with same name. Xenon should converge them as one.
    DefaultDeployment.createDefaultDeployment(
        peers.toArray(new String[peers.size()]),
        photonControllereConfig.getDeployerConfig(),
        testEnvironment.getHosts()[1]);

    DefaultDeployment.createDefaultDeployment(
        peers.toArray(new String[peers.size()]),
        photonControllereConfig.getDeployerConfig(),
        testEnvironment.getHosts()[2]);

    ServiceHostUtils.waitForServiceAvailability(
        testEnvironment.getHosts()[0],
        SERVICES_STARTUP_TIMEOUT, defaultDeploymentSelfLink);
  }
}
