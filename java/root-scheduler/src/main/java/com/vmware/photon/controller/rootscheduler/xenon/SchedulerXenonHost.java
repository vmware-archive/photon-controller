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

package com.vmware.photon.controller.rootscheduler.xenon;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.InMemoryConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ScoreCalculator;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTaskService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements the Xenon service host object
 * for the Root-Scheduler service.
 */
@Singleton
public class SchedulerXenonHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider,
    HostClientProvider,
    ScoreCalculatorProvider,
    ConstraintCheckerProvider,
    CloudStoreClientProvider {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerXenonHost.class);
  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  private final HostClientFactory hostClientFactory;
  private final ScoreCalculator scoreCalculator;
  private final XenonRestClient cloudStoreClient;
  private final CloudStoreHelper cloudStoreHelper;
  private ConstraintChecker checker;

  @SuppressWarnings("rawtypes")
  public static final Class[] FACTORY_SERVICES = {
      RootNamespaceService.class,
      StatusService.class
  };

  @Inject
  public SchedulerXenonHost(XenonConfig xenonConfig,
                            HostClientFactory hostClientFactory,
                            Config config,
                            ConstraintChecker checker,
                            XenonRestClient xenonRestClient,
                            CloudStoreHelper cloudStoreHelper) throws Throwable {
    super(xenonConfig);
    this.hostClientFactory = hostClientFactory;
    this.scoreCalculator = new ScoreCalculator(config);
    this.cloudStoreClient = xenonRestClient;
    this.cloudStoreHelper = cloudStoreHelper;
    this.checker = checker;

    if (this.checker instanceof InMemoryConstraintChecker) {
      final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(() -> {
        try {
          this.checker = new InMemoryConstraintChecker(xenonRestClient);
        } catch (Throwable ex) {
          logger.warn("Failed to initialize in-memory constraint checker", ex);
        }
      }, 0, config.getRefreshIntervalSec(), TimeUnit.SECONDS);
    }
    logger.info("Initialized scheduler service with {}", this.checker.getClass());
  }

  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
  }

  @Override
  public ScoreCalculator getScoreCalculator() {
    return scoreCalculator;
  }

  @Override
  public ConstraintChecker getConstraintChecker() {
    return checker;
  }

  @Override
  public XenonRestClient getCloudStoreClient() {
    return cloudStoreClient;
  }

  public CloudStoreHelper getCloudStoreHelper() {
    return this.cloudStoreHelper;
  }

  /**
   * This method starts the default Xenon core services and the scheduler-specific factory service
   * factories.
   *
   * @return
   * @throws Throwable
   */
  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    super.startFactory(PlacementTaskService.class, PlacementTaskService::createFactory);
    ServiceHostUtils.startServices(this, getFactoryServices());

    return this;
  }

  /**
   * This method returns whether the services started above have come up.
   *
   * @return
   */
  @Override
  public boolean isReady() {
    try {
      return ServiceHostUtils.areServicesReady(
          this, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, getFactoryServices());
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }
}
