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

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;

/**
 * Tests {@link SystemConfig}.
 */
public class SystemConfigTest {
  private TestEnvironment testEnvironment;
  private PhotonControllerXenonHost host;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    testEnvironment = TestEnvironment.create(1);
    host = testEnvironment.getHosts()[0];
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(testEnvironment.getServerSet());
    host.setCloudStoreHelper(cloudStoreHelper);
    SystemConfig.destroyInstance();
    SystemConfig.createInstance(host);
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    testEnvironment.stop();
    testEnvironment = null;
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
    Operation create = Operation
        .createPost(UriUtils.buildUri(host, DeploymentServiceFactory.SELF_LINK))
        .setBody(startState);
    ServiceHostUtils.sendRequestAndWait(host, create, "test");

    assertThat(SystemConfig.getInstance().isPaused(), is(true));
    assertThat(SystemConfig.getInstance().isBackgroundPaused(), is(true));
  }



  @Test(dataProvider = "PauseBackground")
  public void testPauseBackground(DeploymentState state) throws Throwable {
    DeploymentService.State startState = buildState(state);
    Operation post = Operation
        .createPost(UriUtils.buildUri(host, DeploymentServiceFactory.SELF_LINK))
        .setBody(startState);
    ServiceHostUtils.sendRequestAndWait(host, post, "test");

    assertThat(SystemConfig.getInstance().isPaused(), is(false));
    assertThat(SystemConfig.getInstance().isBackgroundPaused(), is(true));
  }

  @DataProvider(name = "PauseBackground")
  public Object[][] getPausedBackground() {
    return new Object[][] {
        {DeploymentState.NOT_DEPLOYED},
        {DeploymentState.ERROR},
        {DeploymentState.BACKGROUND_PAUSED}
    };
  }

  @Test
  public void testNoDeploymentObject() throws Throwable {
    assertThat(SystemConfig.getInstance().isPaused(), is(false));
    assertThat(SystemConfig.getInstance().isBackgroundPaused(), is(true));
  }
}
