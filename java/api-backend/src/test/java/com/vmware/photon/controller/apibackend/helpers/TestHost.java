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
package com.vmware.photon.controller.apibackend.helpers;

import com.vmware.photon.controller.apibackend.ApiBackendFactory;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.nsxclient.NsxClientFactoryProvider;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.LogManager;

/**
 * This class implements helper routines used to test service hosts in isolation.
 */
public class TestHost extends BasicServiceHost
    implements NsxClientFactoryProvider, CloudStoreHelperProvider {

  private Map<Class<? extends Service>, Supplier<FactoryService>> testFactoryServiceMap;
  private NsxClientFactory nsxClientFactory;
  private CloudStoreHelper cloudStoreHelper;

  private TestHost(Map<Class<? extends Service>, Supplier<FactoryService>> testFactoryServiceMap,
                   NsxClientFactory nsxClientFactory,
                   CloudStoreHelper cloudStoreHelper) throws Throwable {
    super();
    this.initialize();
    this.testFactoryServiceMap = testFactoryServiceMap;
    this.nsxClientFactory = nsxClientFactory;
    this.cloudStoreHelper = cloudStoreHelper;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    if (this.cloudStoreHelper != null) {
      this.cloudStoreHelper.setServerSet(
          new StaticServerSet(new InetSocketAddress(getPreferredAddress(), getPort())));
    }

    this.startWithCoreServices();
    ServiceHostUtils.startFactoryServices(this, ApiBackendFactory.FACTORY_SERVICES_MAP);
    ServiceHostUtils.startFactoryServices(this, CloudStoreServiceGroup.FACTORY_SERVICES_MAP);
    ServiceHostUtils.startServices(this, CloudStoreServiceGroup.FACTORY_SERVICES);

    if (this.testFactoryServiceMap != null && !this.testFactoryServiceMap.isEmpty()) {
      ServiceHostUtils.startFactoryServices(this, this.testFactoryServiceMap);
    }

    return this;
  }

  public void setDefaultServiceUri(String serviceUri) {
    this.serviceUri = serviceUri;
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
  public NsxClientFactory getNsxClientFactory() {
    return this.nsxClientFactory;
  }

  @Override
  public CloudStoreHelper getCloudStoreHelper() {
    return this.cloudStoreHelper;
  }

  /**
   * This class implements a builder for {@link TestHost} objects.
   */
  public static class Builder {
    private Map<Class<? extends Service>, Supplier<FactoryService>> testFactoryServiceMap;
    private NsxClientFactory nsxClientFactory;
    private CloudStoreHelper cloudStoreHelper;

    public Builder testFactoryServiceMap(
        Map<Class<? extends Service>, Supplier<FactoryService>> testFactoryServiceMap) {
      this.testFactoryServiceMap = testFactoryServiceMap;
      return this;
    }

    public Builder nsxClientFactory(NsxClientFactory nsxClientFactory) {
      this.nsxClientFactory = nsxClientFactory;
      return this;
    }

    public Builder cloudStoreHelper(CloudStoreHelper cloudStoreHelper) {
      this.cloudStoreHelper = cloudStoreHelper;
      return this;
    }

    public TestHost build() throws Throwable {
      return build(true);
    }

    public TestHost build(boolean autoStart) throws Throwable {
      TestHost host = new TestHost(
          testFactoryServiceMap,
          nsxClientFactory,
          cloudStoreHelper);
      if (autoStart) {
        host.start();
      }

      return host;
    }
  }
}
