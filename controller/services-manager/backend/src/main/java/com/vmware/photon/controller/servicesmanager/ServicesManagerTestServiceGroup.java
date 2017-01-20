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

package com.vmware.photon.controller.servicesmanager;

import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to fake out deployer service group which just implements ServicesManagerFactoryProvider.
 */
public class ServicesManagerTestServiceGroup
    implements XenonServiceGroup,
    ServicesManagerFactoryProvider {

  private static final Logger logger = LoggerFactory.getLogger(ServicesManagerTestServiceGroup.class);

  private static final String SERVICES_MANAGER_TEST_URI = "services-manager-test";

  private final ServicesManagerFactory servicesManagerFactory;

  private PhotonControllerXenonHost photonControllerXenonHost;

  public ServicesManagerTestServiceGroup(ServicesManagerFactory servicesManagerFactory) {
    this.servicesManagerFactory = servicesManagerFactory;
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
  }

  @Override
  public ServicesManagerFactory getServicesManagerFactory() {
    return servicesManagerFactory;
  }

  @Override
  public String getName() {
    return "services-manager-test";
  }

  @Override
  public void start() throws Throwable {
    ServiceHostUtils.startServices(photonControllerXenonHost, ServicesManagerFactory.FACTORY_SERVICES);
  }

  @Override
  public boolean isReady() {
    if (this.servicesManagerFactory == null) {
      return false;
    }
    return true;
  }

}
