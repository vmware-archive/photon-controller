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
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

/**
 * TestEnvironment class hosting a Xenon host.
 */
public class TestEnvironment extends MultiHostEnvironment<TestHost> {

  private TestEnvironment(int hostCount, ApiBackendFactory apiBackendFactory) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new TestHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      hosts[i] = new TestHost(apiBackendFactory);
    }
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {

    private int hostCount;
    private CloudStoreHelper cloudStoreHelper;

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
      return this;
    }

    public Builder cloudStoreServerSet(ServerSet cloudStoreServerSet) {
      this.cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      return this;
    }

    public TestEnvironment build() throws Throwable {
      ApiBackendFactory apiBackendFactory = mock(ApiBackendFactory.class);

      if (this.cloudStoreHelper != null) {
        doReturn(this.cloudStoreHelper).when(apiBackendFactory).createCloudStoreHelper();
      }

      TestEnvironment environment = new TestEnvironment(hostCount, apiBackendFactory);
      environment.start();
      return environment;
    }
  }
}
