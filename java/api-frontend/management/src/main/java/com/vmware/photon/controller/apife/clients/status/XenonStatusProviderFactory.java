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

import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.XenonRestClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Class providing status via REST call to Xenon services.
 */
public class XenonStatusProviderFactory implements StatusProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(XenonStatusProviderFactory.class);
  private final ServerSet serverSet;

  private final ExecutorService executor;
  private final ScheduledExecutorService scheduledExecutorService;

  public XenonStatusProviderFactory(ServerSet serverSet,
                                    ExecutorService executor,
                                    ScheduledExecutorService scheduledExecutorService) {
    this.serverSet = serverSet;
    this.executor = executor;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public ServerSet getServerSet() {
    return this.serverSet;
  }

  @Override
  public StatusProvider create(InetSocketAddress server) throws InternalException {
    logger.info("Creating XenonRestClient as StatusProvider on {}", server);
    XenonRestClient xenonRestClient =
        new XenonRestClient(new StaticServerSet(server), this.executor, this.scheduledExecutorService);
    return new XenonStatusProvider(xenonRestClient);
  }
}
