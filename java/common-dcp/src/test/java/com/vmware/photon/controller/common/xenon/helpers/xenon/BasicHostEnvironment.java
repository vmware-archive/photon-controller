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

package com.vmware.photon.controller.common.xenon.helpers.xenon;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;

/**
 * This class implements an test environment that runs multiple BasicServiceHosts.
 */
public class BasicHostEnvironment extends MultiHostEnvironment<BasicServiceHost> {

  public static final int DEFAULT_MULTI_HOST_COUNT = 3;

  /**
   * Constructor.
   *
   * @param hosts
   */
  private BasicHostEnvironment(BasicServiceHost[] hosts) {
    this.hosts = hosts;
  }

  /**
   * This class implements a builder for {@link BasicServiceHost} objects.
   */
  public static class Builder {
    private BasicServiceHost[] hosts;

    public Builder hostList(BasicServiceHost[] hostList) {
      this.hosts = hostList;
      return this;
    }

    public BasicHostEnvironment build() throws Throwable {
      BasicHostEnvironment environment = new BasicHostEnvironment(this.hosts);
      environment.start();
      return environment;
    }
  }
}
