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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.cloudstore.xenon.CloudStoreXenonHost;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * CloudStoreServerSetChangeListener implements ServerSet.ChangeListener for server addition/removal.
 */
@Singleton
public class CloudStoreServerSetChangeListener implements ServerSet.ChangeListener {

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreServerSetChangeListener.class);

  private final CloudStoreXenonHost dcpHost;

  @Inject
  public CloudStoreServerSetChangeListener(CloudStoreXenonHost dcpHost) {
    this.dcpHost = dcpHost;
  }

  @Override
  public void onServerAdded(InetSocketAddress address) {
    String host = address.getHostString();
    String currentHost = dcpHost.getUri().getHost();
    if (host.equals(currentHost)) {
      logger.info("Skip adding self {}", host);
      return;
    }

    logger.info("joining {} to {}", host, currentHost);
    ServiceHostUtils.joinNodeGroup(dcpHost, host);
  }

  @Override
  public void onServerRemoved(InetSocketAddress address) {
  }
}
