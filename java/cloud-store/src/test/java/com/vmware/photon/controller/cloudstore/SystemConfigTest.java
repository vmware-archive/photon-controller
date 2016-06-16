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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;

/**
 * Tests {@link SystemConfig}.
 */
public class SystemConfigTest {
  private TestEnvironment testEnvironment;
  private DeploymentService service;
  private PhotonControllerXenonHost host;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    service = new DeploymentService();
    testEnvironment = TestEnvironment.create(1);
    host = testEnvironment.getHosts()[0];
    SystemConfig.createInstance(host);
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    testEnvironment.stop();
    testEnvironment = null;
    service = null;
  }

  private DeploymentService.State buildState(DeploymentState desiredState) {
    DeploymentService.State startState = new DeploymentService.State();
    startState.state = desiredState;
    startState.imageDataStoreNames = Collections.singleton("datastore1");
    startState.imageDataStoreUsedForVMs = true;
    startState.statsEnabled = true;
    return startState;
  }

  @Test
  public void testPause() throws Throwable {
    DeploymentService.State startState = buildState(DeploymentState.PAUSED);
    Operation post = Operation
        .createPost(UriUtils.buildUri(host, DeploymentServiceFactory.SELF_LINK))
        .setBody(startState);

    ServiceHostUtils.sendRequestAndWait(host, post, "test");
    waitForIsPaused(SystemConfig.getInstance(), true);
  }

  private void waitForIsPaused(SystemConfig systemConfig, boolean isPaused)
      throws Throwable {
    for (int i = 0; i < 600; i++) {
      Thread.sleep(100);
      if (isPaused == systemConfig.isPaused()) {
        return;
      }
    }

    assertThat(systemConfig.isBackgroundPaused(), is(isPaused));
  }

  @Test
  public void testPauseBackground() throws Throwable {
    DeploymentService.State startState = buildState(DeploymentState.BACKGROUND_PAUSED);
    Operation post = Operation
        .createPost(UriUtils.buildUri(host, DeploymentServiceFactory.SELF_LINK))
        .setBody(startState);

    ServiceHostUtils.sendRequestAndWait(host, post, "test");
    waitForIsBackgroundPaused(SystemConfig.getInstance(), true);
  }

  private void waitForIsBackgroundPaused(SystemConfig systemConfig, boolean isBackgroundPaused)
      throws Throwable {
    for (int i = 0; i < 300; i++) {
      Thread.sleep(100);
      if (isBackgroundPaused == systemConfig.isBackgroundPaused()) {
        return;
      }
    }

    assertThat(systemConfig.isBackgroundPaused(), is(isBackgroundPaused));
  }
}
