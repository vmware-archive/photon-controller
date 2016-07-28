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

package com.vmware.photon.controller.api.frontend.clients.status;

import com.vmware.photon.controller.api.frontend.exceptions.internal.ComponentClientCreationException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import org.apache.thrift.async.TAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;

/**
 * Class generating thrift client.
 */
public class ThriftClientFactory implements StatusProviderFactory {
  private static final Logger logger = LoggerFactory.getLogger(ThriftClientFactory.class);
  private final Class<?> clientClass;
  private final ClientPoolFactory clientPoolFactory;
  private final ClientProxyFactory clientProxyFactory;
  private final ServerSet serverSet;
  private final String serviceName;

  public ThriftClientFactory(ServerSet serverSet,
                             ClientPoolFactory clientPoolFactory,
                             ClientProxyFactory clientProxyFactory,
                             Class<?> clientClass,
                             String serviceName) {
    this.serverSet = serverSet;
    this.clientPoolFactory = clientPoolFactory;
    this.clientProxyFactory = clientProxyFactory;
    this.clientClass = clientClass;
    this.serviceName = serviceName;
  }

  @Override
  public ServerSet getServerSet() {
    return serverSet;
  }

  /**
   * Setting client for a given single server of given component.
   *
   * @param server
   * @return
   */
  public StatusProvider create(InetSocketAddress server) throws InternalException {
    ClientProxy proxy = createClientProxy(server, this.clientPoolFactory, this.clientProxyFactory);
    return createClient(proxy);
  }

  private <C extends TAsyncClient> StatusProvider createClient(ClientProxy proxy) throws InternalException {
    try {
      Constructor constructor = this.clientClass.getConstructor(ClientProxy.class);
      return (StatusProvider) constructor.newInstance(proxy);
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
        InvocationTargetException ex) {
      logger.warn("Failed to create instance for class {} using reflection , Exception = {}",
          this.clientClass,
          ex);
      throw new ComponentClientCreationException(
          String.format("Failed to create a %s.", this.clientClass.getName()), ex);
    }
  }

  private ClientProxy createClientProxy(InetSocketAddress server,
                                        ClientPoolFactory clientPoolFactory, ClientProxyFactory clientProxyFactory) {
    ClientPool clientpool =
        clientPoolFactory.create(
            new StaticServerSet(server),
            new ClientPoolOptions().setMaxClients(1).setMaxWaiters(1).setServiceName(this.serviceName));
    ClientProxy clientProxy =
        clientProxyFactory.create(clientpool);
    return clientProxy;
  }
}
