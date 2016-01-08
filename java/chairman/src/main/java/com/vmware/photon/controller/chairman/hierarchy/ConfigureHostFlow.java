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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.RolesRegistry;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.ConfigureResultCode;
import static com.vmware.photon.controller.host.gen.Host.AsyncClient;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Host configuration flow.
 */
public class ConfigureHostFlow implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(ConfigureHostFlow.class);
  private static final long CONFIGURE_TIMEOUT_MS = 30000;
  static DataDictionary rolesDictionary;
  static TSerializer serializer = new TSerializer();
  private final ClientPoolFactory<AsyncClient> clientPoolFactory;
  private final ClientProxyFactory<AsyncClient> clientProxyFactory;
  private final Host host;
  private final ConfigureRequest configRequest;

  @Inject
  public ConfigureHostFlow(ClientPoolFactory<AsyncClient> clientPoolFactory,
                           ClientProxyFactory<AsyncClient> clientProxyFactory,
                           @Assisted Host host,
                           @Assisted ConfigureRequest configRequest,
                           @RolesRegistry DataDictionary rolesDictionary) {
    this.clientPoolFactory = clientPoolFactory;
    this.clientProxyFactory = clientProxyFactory;
    this.host = host;
    this.configRequest = configRequest;
    this.rolesDictionary = rolesDictionary;
  }

  @Override
  public void run() {
    if (host.isMissing()) {
      logger.warn("Attempted to configure host {}, which has been marked as missing; trying to configure anyways",
              host.getId());
    }

    InetSocketAddress address = InetSocketAddress.createUnresolved(
        host.getAddress(), host.getPort());
    ServerSet hostServerSet = new StaticServerSet(address);

    String serviceName;
    if (host.getId().equals(HierarchyManager.ROOT_SCHEDULER_HOST_ID)) {
      serviceName = "RootScheduler";
    } else {
      serviceName = "Host";
    }

    ClientPoolOptions poolOptions = new ClientPoolOptions()
        .setMaxClients(1)
        .setMaxWaiters(1)
        .setServiceName(serviceName)
        .setTimeout(10, TimeUnit.SECONDS);

    ClientPool<AsyncClient> clientPool = clientPoolFactory.create(hostServerSet, poolOptions);
    ClientProxy<AsyncClient> clientProxy = clientProxyFactory.create(clientPool);

    AsyncClient client = clientProxy.get();
    ConfigureResponseHandler handler = new ConfigureResponseHandler(host, configRequest);

    try {
      // Host will be marked as not configured again if configure fails. Marking it as not configured here
      // allows the next scan to skip it if configuration is still in progress.
      host.setConfigured(true);
      logger.debug("Configuring {}: {}", host, configRequest);
      client.setTimeout(CONFIGURE_TIMEOUT_MS);
      client.configure(configRequest, handler);
      handler.await();
    } catch (TException e) {
      logger.error("Error configuring host {}: {}", host.getId(), e);
      host.setConfigured(false);
    } catch (InterruptedException e) {
      logger.error("Interrupted while trying to configure host {}", host.getId());
      host.setConfigured(false);
      Thread.currentThread().interrupt();
    } finally {
      clientPool.close();
      try {
        hostServerSet.close();
      } catch (IOException e) {
        logger.warn("Error while closing server set: ", e);
      }
    }
  }

  static class ConfigureResponseHandler implements AsyncMethodCallback<AsyncClient.configure_call> {
    private final Host host;
    private final ConfigureRequest request;
    private final CountDownLatch done;

    ConfigureResponseHandler(Host host, ConfigureRequest request) {
      this.host = host;
      this.request = request;
      done = new CountDownLatch(1);
    }

    public void await() throws InterruptedException {
      done.await();
    }

    @Override
    public void onComplete(AsyncClient.configure_call response) {
      try {
        ConfigureResponse result = response.getResult();
        if (result.getResult() == ConfigureResultCode.OK) {
          logger.info("Configured {}: {} {}", host, request, result);
        } else {
          logger.error("Failed to configure {}: {} {}", host, request, result.getResult());
          host.setConfigured(false);
        }
      } catch (TException e) {
        handleError(e);
      } finally {
        done.countDown();
      }
    }

    @Override
    public void onError(Exception e) {
      handleError(e);
      done.countDown();
    }

    private void handleError(Exception e) {
      logger.error("Error configuring host {}: {}", host.getId(), e);
      host.setConfigured(false);
    }
  }
}
