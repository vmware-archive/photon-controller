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

package com.vmware.photon.controller.apife.config;

import com.vmware.photon.controller.common.metrics.GraphiteConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import io.dropwizard.Configuration;

/**
 * API Front End Server Configuration.
 */
public abstract class ApiFeConfiguration extends Configuration {

  public abstract AuthConfig getAuth();

  public abstract RootSchedulerConfig getRootScheduler();

  public abstract int getBackgroundWorkers();

  public abstract int getBackgroundWorkersQueueSize();

  public abstract ZookeeperConfig getZookeeper();

  public abstract GraphiteConfig getGraphite();

  public abstract String getRegistrationAddress();

  public abstract ImageConfig getImage();

  public abstract StatusConfig getStatusConfig();

  public abstract boolean useDcpBackend();

  public abstract PaginationConfig getPaginationConfig();
}
