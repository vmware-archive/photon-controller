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

import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ScoreCalculator;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTaskService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What used to be the SchedulerXenonHost.
 */
public class SchedulerServiceGroup
    implements XenonServiceGroup,
    ScoreCalculatorProvider,
    ConstraintCheckerProvider {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerServiceGroup.class);
  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  private final ScoreCalculator scoreCalculator;
  private ConstraintChecker checker;
  private PhotonControllerXenonHost photonControllerXenonHost;

  @SuppressWarnings("rawtypes")
  public static final Class[] FACTORY_SERVICES = {};

  public SchedulerServiceGroup(RootSchedulerConfig config) throws Throwable {
    this.scoreCalculator = new ScoreCalculator(config);
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
  public String getName() {
    return "scheduler";
  }

  /**
   * This method starts the default Xenon core services and the scheduler-specific factory service
   * factories.
   *
   * @return
   * @throws Throwable
   */
  @Override
  public void start() throws Throwable {
    // Start all the factories
    photonControllerXenonHost.startFactory(PlacementTaskService.class, PlacementTaskService::createFactory);
    ServiceHostUtils.startServices(photonControllerXenonHost, getFactoryServices());
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
          photonControllerXenonHost, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, getFactoryServices());
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
    this.checker = new CloudStoreConstraintChecker(photonControllerXenonHost);
  }

  @SuppressWarnings("rawtypes")
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }
}
