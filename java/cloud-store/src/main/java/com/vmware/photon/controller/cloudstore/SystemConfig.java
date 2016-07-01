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
import com.vmware.photon.controller.common.provider.SystemConfigProvider;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Photon-Controller configuration.
 */
public class SystemConfig implements SystemConfigProvider {
  private static final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

  private static SystemConfig instance = null;
  private boolean isPaused = false;
  private boolean isBackgroundPaused = false;

  private PhotonControllerXenonHost xenonHost;


  private SystemConfig(PhotonControllerXenonHost serviceHost) {
    this.xenonHost = serviceHost;
  }

  public static SystemConfig getInstance() {
    return instance;
  }

  public static SystemConfig createInstance(PhotonControllerXenonHost xenonHost) {
    if (instance == null) {
      instance = new SystemConfig(xenonHost);
      xenonHost.setSystemConfigProvider(instance);
    }
    return instance;
  }

  public void markPauseStateLocally(DeploymentService.State deploymentService) {
    if (deploymentService.state == DeploymentState.BACKGROUND_PAUSED) {
      markPauseStateLocally(true, false);
    } else if (deploymentService.state == DeploymentState.PAUSED) {
      markPauseStateLocally(false, true);
    } else {
      markPauseStateLocally(false, false);
    }
  }

  // This is an optimization on the local node. As soon as user kicks a Pause/BackgroundPause/Resume/, we mark locally
  // rather than querying it again.
  public void markPauseStateLocally(boolean isBackgroundPaused, boolean isPaused) {
    instance.isBackgroundPaused = isBackgroundPaused;
    instance.isPaused = isPaused;
    logger.info("SystemConfig mark local... isBackgroundPaused:{}  isPaused{}", isBackgroundPaused, isPaused);
  }

  @Override
  public boolean isPaused() {
    return instance.isPaused;
  }

  @Override
  public boolean isBackgroundPaused()  {
    return instance.isPaused || instance.isBackgroundPaused;
  }
}
