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
package com.vmware.photon.controller.clustermanager.helpers;

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

import java.util.logging.LogManager;

/**
 * This class implements helper routines used to test service hosts in isolation.
 */
public class TestHost extends BasicServiceHost implements ClusterManagerFactoryProvider {

  private ClusterManagerFactory clusterManagerFactory;

  public TestHost(ClusterManagerFactory clusterManagerFactory) throws Throwable {
    super();
    this.clusterManagerFactory = clusterManagerFactory;
    this.initialize();
  }

  public static TestHost create() throws Throwable {
    return create(null);
  }

  public static TestHost create(ClusterManagerFactory clusterManagerFactory) throws Throwable {
    TestHost host = new TestHost(clusterManagerFactory);
    host.start();
    return host;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    this.startWithCoreServices();
    ServiceHostUtils.startServices(this, ClusterManagerFactory.FACTORY_SERVICES);

    return this;
  }

  @Override
  public void destroy() throws Throwable {
    super.destroy();
    LogManager.getLogManager().reset();
  }

  @Override
  public Operation sendRequestAndWait(Operation op) throws Throwable {
    Operation operation = super.sendRequestAndWait(op);
    // For tests we check status code 200 to see if the response is OK
    // If nothing is changed in patch, it returns 304 which means not modified.
    // We will treat 304 as 200
    if (operation.getStatusCode() == 304) {
      operation.setStatusCode(200);
    }
    return operation;
  }

  @Override
  public ClusterManagerFactory getClusterManagerFactory() {
    return this.clusterManagerFactory;
  }
}
