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

import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import static org.testng.Assert.assertTrue;

/**
 * TestEnvironment class hosting a Xenon host.
 */
public class TestEnvironment extends MultiHostEnvironment<TestHost> {

  private TestEnvironment(int hostCount,
                          NsxClientFactory nsxClientFactory,
                          CloudStoreHelper cloudStoreHelper) throws Throwable {

    assertTrue(hostCount > 0);
    hosts = new TestHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      hosts[i] = new TestHost.Builder()
        .nsxClientFactory(nsxClientFactory)
          .cloudStoreHelper(cloudStoreHelper)
          .build(false);
    }
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {

    private int hostCount;
    private NsxClientFactory nsxClientFactory;
    private CloudStoreHelper cloudStoreHelper;

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
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

    public TestEnvironment build() throws Throwable {
      TestEnvironment environment = new TestEnvironment(
          hostCount,
          nsxClientFactory,
          cloudStoreHelper);

      environment.start();
      return environment;
    }
  }
}
