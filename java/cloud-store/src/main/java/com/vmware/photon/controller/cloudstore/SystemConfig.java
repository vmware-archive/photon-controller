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
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Manages the Photon-Controller configuration.
 */
public class SystemConfig implements SystemConfigProvider {
  private static final Logger logger = LoggerFactory.getLogger(SystemConfig.class);
  private static final String REFERRER_PATH = "/systemconfigquery";
  // 1 minute
  private static final int pausedStateCheckerFrequencyMs = 60000;

  private static SystemConfig instance = null;
  private boolean isPaused = false;
  private boolean isBackgroundPaused = false;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private PhotonControllerXenonHost xenonHost;


  private SystemConfig(PhotonControllerXenonHost serviceHost) {
    this.xenonHost = serviceHost;
    scheduler.scheduleAtFixedRate(new PausedStateChecker(),
        0, pausedStateCheckerFrequencyMs, MILLISECONDS);
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

  // Forces an extra check before 30 sec is up.
  public void runCheck() {
    this.scheduler.submit(new PausedStateChecker());
  }

  @Override
  public boolean isPaused() {
    return instance.isPaused;
  }

  @Override
  public boolean isBackgroundPaused()  {
    return instance.isPaused || instance.isBackgroundPaused;
  }

  private class PausedStateChecker implements Runnable {
    public void run() {
      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(DeploymentService.State.class)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      Operation queryPost = Operation
          .createPost(UriUtils.buildUri(xenonHost,
              com.vmware.photon.controller.common.xenon.ServiceUriPaths.CORE_QUERY_TASKS))
          .setBody(queryTask);

      try {
        Operation operation = ServiceHostUtils.sendRequestAndWait(xenonHost, queryPost, REFERRER_PATH);
        Map<String, Object> deploymentDocuments = operation.getBody(QueryTask.class).results.documents;
        if (deploymentDocuments.isEmpty()) {
          logger.debug("SystemConfig check... Could not find any deployment documents, returning");
          return;
        }
        Map<String, DeploymentService.State> deploymentMap = deploymentDocuments.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                (entry) -> Utils.fromJson((String) entry.getValue(), DeploymentService.State.class)));
        DeploymentService.State deployment = deploymentMap.entrySet().iterator().next().getValue();
        logger.info("SystemConfig check... DeploymentState:{}", deployment.state);
        if (deployment.state == DeploymentState.BACKGROUND_PAUSED) {
          isBackgroundPaused = true;
          isPaused = false;
        } else if (deployment.state == DeploymentState.PAUSED) {
          isPaused = true;
          isBackgroundPaused = false;
        } else {
          isBackgroundPaused = false;
          isPaused = false;
        }
      } catch (InterruptedException e) {
        logger.error("Ignoring this since we will do another query", e);
      } catch (TimeoutException e) {
        logger.error("Ignoring this since we will do another query", e);
      } catch (BadRequestException e) {
        logger.error("Ignoring this since we will do another query", e);
      } catch (DocumentNotFoundException e) {
        logger.error("Ignoring this since we will do another query", e);
      }
    }
  }
}
