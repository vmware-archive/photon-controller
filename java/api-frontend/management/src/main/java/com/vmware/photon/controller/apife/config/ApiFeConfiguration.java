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

/**
 * API Front End Server Configuration interface.
 */
public interface ApiFeConfiguration {

  public int getApifePort();

  public int getXenonPort();

  public AuthConfig getAuth();

  public RootSchedulerConfig getRootScheduler();

  public ZookeeperConfig getZookeeper();

  public int getBackgroundWorkers();

  public int getBackgroundWorkersQueueSize();

  public GraphiteConfig getGraphite();

  public ImageConfig getImage();

  public StatusConfig getStatusConfig();

  public boolean useXenonBackend();

  public PaginationConfig getPaginationConfig();

  public boolean useVirtualNetwork();
}
