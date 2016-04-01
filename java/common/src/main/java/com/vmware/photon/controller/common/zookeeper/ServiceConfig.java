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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Class manages configuration of a service via ZK using znode names under path /config/[serviceName].
 */
public class ServiceConfig implements PathChildrenCacheListener {

  private static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);
  private static final String PAUSED_STRING = "PAUSED";
  private static final byte[] PAUSED_BYTES = PAUSED_STRING.getBytes();
  private static final String PAUSED_BACKGROUND_STRING = "PAUSED_BACKGROUND";
  private static final byte[] PAUSED_BACKGROUND_BYTES = PAUSED_BACKGROUND_STRING.getBytes();
  private static final String STATUS_ZK_PATH = "status";
  private final String serviceName;
  private final DataDictionary serviceConfig;
  private final PathChildrenCache configCache;
  private final String serviceConfigZKPath;
  private final String serviceStatusZKPath;

  @Inject
  public ServiceConfig(CuratorFramework zkClient,
                       @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory,
                       @Assisted String serviceName) throws Exception {
    this.serviceName = serviceName;
    serviceConfigZKPath = "config/" + serviceName;
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("ZkServiceConfigPathChildrenCache" + "-%d")
        .setDaemon(true)
        .build();
    ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    configCache = childrenCacheFactory.createPathCache(ZKPaths.makePath(serviceConfigZKPath, ""), executor);
    configCache.getListenable().addListener(this);
    configCache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
    this.serviceConfig = new DataDictionary(zkClient, executor, serviceConfigZKPath);
    serviceStatusZKPath = ZKPaths.makePath(this.serviceConfigZKPath, STATUS_ZK_PATH);
  }

  /**
   * Pause the target service by adding entry in /config/[serviceName]/status/PAUSED.
   *
   * @throws Exception
   */
  public void pause() throws Exception {
    this.serviceConfig.write(STATUS_ZK_PATH, PAUSED_BYTES);
    logger.info("Service {} is paused", serviceName);
  }

  /**
   * Pause the target service by adding entry in /config/[serviceName]/status/PAUSED_BACKGROUND.
   *
   * @throws Exception
   */
  public void pauseBackground() throws Exception {
    this.serviceConfig.write(STATUS_ZK_PATH, PAUSED_BACKGROUND_BYTES);
    logger.info("Service {} background processing is paused", serviceName);
  }

  /**
   * Resume the target service by deleting entry in /config/[serviceName]/status.
   *
   * @throws Exception
   */
  public void resume() throws Exception {
    this.serviceConfig.write(STATUS_ZK_PATH, null);
    logger.info("Service {} is resumed", serviceName);
  }

  /**
   * Return true if the target service is fully functional.
   *
   * @return
   * @throws Exception
   */
  private boolean isServiceFullyFunctional() throws Exception {
    this.configCache.rebuild();
    return null == this.configCache.getCurrentData(this.serviceStatusZKPath);
  }

  /**
   * Return if the target service is paused.
   *
   * @return
   * @throws Exception
   */
  public boolean isPaused() throws Exception {
    if (!isServiceFullyFunctional()) {
      ChildData statusData = this.configCache.getCurrentData(this.serviceStatusZKPath);
      if (statusData != null) {
        return PAUSED_STRING.equals(new String(statusData.getData()));
      }
      String errMsg = String.format("No entry (null) for %s is unexpected. Seems potential race condition",
          this.serviceStatusZKPath);
      logger.info(errMsg);
      throw new NullPointerException(errMsg);
    }
    return false;
  }

  /**
   * Return true if the target service's background processing is paused.
   *
   * @return
   * @throws Exception
   */
  public boolean isBackgroundPaused() throws Exception {
    if (!isServiceFullyFunctional()) {
      ChildData statusData = this.configCache.getCurrentData(this.serviceStatusZKPath);
      if (statusData != null) {
        return isPaused() || PAUSED_BACKGROUND_STRING.equals(new String(statusData.getData()));
      }
      String errMsg = String.format("No entry (null) for %s is unexpected. Seems potential race condition",
          this.serviceStatusZKPath);
      logger.info(errMsg);
      throw new NullPointerException(errMsg);
    }
    return false;
  }

  @Override
  public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
    logger.debug("Child event: {}", event);
  }

}
