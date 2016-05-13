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

package com.vmware.photon.controller.apife.clients.status;

import com.vmware.photon.controller.apife.HousekeeperServerSet;
import com.vmware.photon.controller.apife.backends.clients.DeployerXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.XenonRestClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;

/**
 * Class providing status via REST call to DCP services.
 */
public class DcpStatusProviderFactory implements StatusProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(DcpStatusProviderFactory.class);
  private final ServerSet serverSet;

  private final ExecutorService executor;

  public DcpStatusProviderFactory(ServerSet serverSet, ExecutorService executor) {
    this.serverSet = serverSet;
    this.executor = executor;
  }

  @Override
  public ServerSet getServerSet() {
    return this.serverSet;
  }

  @Override
  public StatusProvider create(InetSocketAddress server) throws InternalException {
    logger.info("Creating DcpRestClient as StatusProvider on {}", server);
    XenonRestClient dcpRestClient = new XenonRestClient(new StaticServerSet(server), this.executor);
    //TODO(adev): Remove this after thrift removal
    try {
      logger.info("*******Creating DcpRestClient as StatusProvider {}", server.getPort());
      if (server.getPort() == 18000 || server.getPort() == 16000) {
        dcpRestClient = new DeployerXenonRestClient(new StaticServerSet(server), this.executor);
        logger.info("Creating DcpRestClient as StatusProvider on deployer");
      } else if (HousekeeperServerSet.class.isAssignableFrom(serverSet.getClass())) {
        dcpRestClient = new HousekeeperXenonRestClient(new StaticServerSet(server), this.executor);
        logger.info("Creating DcpRestClient as StatusProvider on housekeeper");
      }
    } catch (URISyntaxException ex){

    }
    /////////////////////////////
    return new DcpStatusProvider(dcpRestClient);
  }
}
