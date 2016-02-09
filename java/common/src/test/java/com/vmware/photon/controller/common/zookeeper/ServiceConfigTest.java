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

package com.vmware.photon.controller.common.zookeeper;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link ServiceConfig}.
 */
public class ServiceConfigTest extends BaseTestWithRealZookeeper {

  /**
   * Tests {@link ServiceConfig#pause()}.
   */
  @Test
  public void testPause() throws Throwable {
    zkClient.start();

    try {
      ServiceConfig serviceConfig = new ServiceConfig(zkClient, new PathChildrenCacheFactory(zkClient, null), "apife");
      assertThat(serviceConfig.isPaused(), is(false));

      serviceConfig.pause();
      waitForIsPaused(serviceConfig, true);

      serviceConfig.resume();
      waitForIsPaused(serviceConfig, false);
    } finally {
      zkClient.close();
    }
  }

  private void waitForIsPaused(ServiceConfig serviceConfig, boolean isPaused)
      throws Throwable {
    for (int i = 0; i < 50; i++) {
      Thread.sleep(2);
      if (isPaused == serviceConfig.isPaused()) {
        return;
      }
    }

    assertThat(serviceConfig.isPaused(), is(isPaused));
  }

  /**
   * Tests {@link ServiceConfig#pauseBackground()}.
   */
  @Test
  public void testPauseBackground() throws Throwable {
    zkClient.start();

    try {
      ServiceConfig serviceConfig = new ServiceConfig(zkClient, new PathChildrenCacheFactory(zkClient, null), "apife");
      assertThat(serviceConfig.isPaused(), is(false));
      assertThat(serviceConfig.isBackgroundPaused(), is(false));

      serviceConfig.pauseBackground();
      waitForIsBackgroundPaused(serviceConfig, true);
      assertThat(serviceConfig.isPaused(), is(false));

      serviceConfig.resume();
      waitForIsBackgroundPaused(serviceConfig, false);
      assertThat(serviceConfig.isPaused(), is(false));

      serviceConfig.pause();
      waitForIsPaused(serviceConfig, true);
      assertThat(serviceConfig.isBackgroundPaused(), is(true));

      serviceConfig.resume();
      waitForIsPaused(serviceConfig, false);
      assertThat(serviceConfig.isBackgroundPaused(), is(false));
    } finally {
      zkClient.close();
    }
  }

  private void waitForIsBackgroundPaused(ServiceConfig serviceConfig, boolean isBackgroundPaused)
      throws Throwable {
    for (int i = 0; i < 50; i++) {
      Thread.sleep(2);
      if (isBackgroundPaused == serviceConfig.isBackgroundPaused()) {
        return;
      }
    }

    assertThat(serviceConfig.isBackgroundPaused(), is(isBackgroundPaused));
  }
}
