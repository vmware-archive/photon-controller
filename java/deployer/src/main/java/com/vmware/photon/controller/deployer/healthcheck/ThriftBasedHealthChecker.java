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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.MultiplexedProtocolFactory;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Implements health-check for components that expose the get_status thrift api.
 */
public class ThriftBasedHealthChecker implements HealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(ThriftBasedHealthChecker.class);

  private final ContainersConfig.ContainerType containerType;
  private final String ipAddress;
  private final int port;

  public ThriftBasedHealthChecker(ContainersConfig.ContainerType containerType, String ipAddress, int port) {
    this.containerType = containerType;
    this.ipAddress = ipAddress;
    this.port = port;
  }

  @Override
  public boolean isReady() {
    Status response = buildStatusProvider().getStatus();
    logger.info("{} GetStatus returned: {}", this.containerType, response);

    return StatusType.READY == response.getType();
  }

  @VisibleForTesting
  protected StatusProvider buildStatusProvider() {
    throw new RuntimeException(String.format("%s does not support thrift health check", containerType));
  }

  private <X, C extends TAsyncClient> X getThriftClient(
      Class<X> clientClass,
      final Class<C> asyncClass,
      final String serviceName) {

    logger.debug("Constructing Thrift client for: {} [{}:{}]", clientClass.getCanonicalName(), ipAddress, port);

    try {
      Constructor asyncClassCtor = asyncClass.getConstructor(
          new Class[]{
              TProtocolFactory.class,
              TAsyncClientManager.class,
              TNonblockingTransport.class});

      final C asyncClient = (C) asyncClassCtor.newInstance(
          new Object[]{
              new MultiplexedProtocolFactory(new TCompactProtocol.Factory(), serviceName),
              new TAsyncClientManager(),
              new TNonblockingSocket(ipAddress, port)
          });

      final ClientProxy<C> clientProxy = new ClientProxy<C>() {
        @Override
        public C get() {
          return asyncClient;
        }
      };

      Constructor clientClassCtor = clientClass.getConstructor(new Class[]{ClientProxy.class});
      return (X) clientClassCtor.newInstance(new Object[]{clientProxy});
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
