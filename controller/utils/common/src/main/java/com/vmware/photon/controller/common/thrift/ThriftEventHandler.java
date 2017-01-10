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

package com.vmware.photon.controller.common.thrift;

import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for managing the thrift server lifecycle.
 * <p/>
 * It tries to join the service cluster (node) when the thrift server is started. Once joined it will fire the
 * {@link ServiceNodeEventHandler#onJoin()} on the registered handler.
 * <p/>
 * When the service membership is revoked it will fire
 * {@link ServiceNodeEventHandler#onLeave()}.
 * <p/>
 * When the service loses its membership it will fire
 * {@link ServiceNodeEventHandler#onLeave()} AND try to join again.
 */
public class ThriftEventHandler implements TServerEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ThriftEventHandler.class);
  private static final long retryIntervalMsec = 5000;

  private final ServiceNode serviceNode;
  private final ServiceNodeEventHandler serviceNodeEventHandler;

  @Inject
  public ThriftEventHandler(@Assisted ServiceNodeEventHandler serviceNodeEventHandler,
                            @Assisted ServiceNode serviceNode) {
    this.serviceNode = serviceNode;
    this.serviceNodeEventHandler = serviceNodeEventHandler;
  }

  @Override
  public void preServe() {
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec, serviceNodeEventHandler);
  }

  @Override
  public ServerContext createContext(TProtocol input, TProtocol output) {
    return null;
  }

  @Override
  public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output) {
  }

  @Override
  public void processContext(ServerContext serverContext, TTransport inputTransport, TTransport outputTransport) {
  }

}
