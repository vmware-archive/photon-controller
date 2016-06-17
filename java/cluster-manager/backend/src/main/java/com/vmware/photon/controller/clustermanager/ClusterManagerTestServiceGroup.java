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

package com.vmware.photon.controller.clustermanager;

import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to fake out deployer service group which just implements ClusterManagerFactoryProvider.
 */
public class ClusterManagerTestServiceGroup
    implements XenonServiceGroup,
    ClusterManagerFactoryProvider {

  private static final Logger logger = LoggerFactory.getLogger(ClusterManagerTestServiceGroup.class);

  private static final String CLUSTER_MANAGER_TEST_URI = "cluster-manager-test";

  private final ClusterManagerFactory clusterManagerFactory;

  private PhotonControllerXenonHost photonControllerXenonHost;

  public ClusterManagerTestServiceGroup (ClusterManagerFactory clusterManagerFactory) {
    this.clusterManagerFactory = clusterManagerFactory;
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
  }

  @Override
  public ClusterManagerFactory getClusterManagerFactory() {
    return clusterManagerFactory;
  }

  @Override
  public String getName() {
    return "cluster-manager-test";
  }

  @Override
  public void start() throws Throwable {
    ServiceHostUtils.startServices(photonControllerXenonHost, ClusterManagerFactory.FACTORY_SERVICES);
  }

  @Override
  public boolean isReady() {
    if (this.clusterManagerFactory == null) {
      return false;
    }
    return true;
  }

}
