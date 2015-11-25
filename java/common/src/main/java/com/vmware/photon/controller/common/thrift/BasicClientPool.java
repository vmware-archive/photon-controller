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

import static com.vmware.photon.controller.common.Constants.VOID;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Basic implementation of {@link ClientPool}.
 * It is a static pool without adding/removing or reusing socket.
 *
 * @param <C> thrift async client type
 */
public class BasicClientPool<C extends TAsyncClient> implements ClientPool<C> {

  private static final Logger logger = LoggerFactory.getLogger(BasicClientPool.class);

  private final SecureRandom random;
  private final TAsyncClientFactory<C> clientFactory;
  private final TProtocolFactory protocolFactory;
  private final ThriftFactory thriftFactory;
  private final ScheduledExecutorService scheduledExecutor;
  private final ClientPoolOptions options;
  private final InetSocketAddress[] availableServers;
  private final Map<C, TNonblockingTransport> clientTransportMap;
  private final Queue<Promise<C>> promises;
  private boolean closed;

  @Inject
  public BasicClientPool(SecureRandom random,
                         TAsyncClientFactory<C> clientFactory,
                         TProtocolFactory protocolFactory,
                         ThriftFactory thriftFactory,
                         @ClientPoolTimer ScheduledExecutorService scheduledExecutor,
                         @Assisted Set<InetSocketAddress> servers,
                         @Assisted ClientPoolOptions options) {
    this.random = random;
    this.clientFactory = clientFactory;
    this.protocolFactory = protocolFactory;
    this.thriftFactory = thriftFactory;
    this.scheduledExecutor = scheduledExecutor;
    this.options = new ClientPoolOptions(options);
    this.clientTransportMap = new HashMap<>();
    this.availableServers = servers.toArray(new InetSocketAddress[servers.size()]);
    this.promises = new LinkedList<>();
    this.closed = false;
  }

  @Override
  public synchronized ListenableFuture<C> acquire() {
    if (promises.size() < options.getMaxWaiters()) {
      SettableFuture<C> future = SettableFuture.create();
      Promise<C> promise = new Promise<>(future);
      promises.add(promise);
      processPromises();
      if (options.getTimeoutMs() > 0 && !future.isDone()) {
        setTimeout(promise);
      }
      return future;
    }

    return Futures.immediateFailedFuture(new ClientPoolException("Too many waiters"));
  }

  @Override
  public synchronized boolean isClosed() {
      return this.closed;
  }

  @Override
  public synchronized void close() {
    logger.debug("{}, closing client pool {}", options.getServiceName(), System.identityHashCode(this));
    Preconditions.checkState(!closed);

    Iterator<Promise<C>> promiseIterator = promises.iterator();
    while (promiseIterator.hasNext()) {
      Promise<C> promise = promiseIterator.next();
      promise.setException(new ClientPoolException("Client pool closing"));
      promiseIterator.remove();
    }

    for (TNonblockingTransport transport : clientTransportMap.values()) {
      transport.close();
    }

    clientTransportMap.clear();
    closed = true;
  }

  @Override
  public synchronized void release(C client, boolean healthy) {
    logger.debug("{}, client pool {}, releasing: {} healthy: {}",
        options.getServiceName(), System.identityHashCode(this), client, healthy);
    if (closed) {
      logger.warn("{}, client pool {} is closed already", options.getServiceName(), System.identityHashCode(this));
    } else {
      TNonblockingTransport transport = clientTransportMap.remove(client);
      transport.close();
    }

    processPromises();
  }

  @Override
  public synchronized int getWaiters() {
    return promises.size();
  }

  @VisibleForTesting
  protected Queue<Promise<C>> getPromises() {
    return promises;
  }

  @VisibleForTesting
  protected Map<C, TNonblockingTransport> getClientTransportMap() {
    return clientTransportMap;
  }

  private void processPromises() {
    while (!promises.isEmpty()) {
      // Find a promise that hasn't timeout yet
      Promise<C> promise = promises.peek();
      if (promise.isDone()) {
        promises.remove();
        continue;
      }

      logger.debug("process promise");
      if (!canCreateClient()) {
        logger.info("{}, client pool {}, not creating new client, request is queued until a client is available: " +
                "client(s) in use {}, request(s) waiting {}, server count {}",
            options.getServiceName(), System.identityHashCode(this), clientTransportMap.size(),
            promises.size(), availableServers.length);
        return;
      }

      try {
        C client = createNewClient();
        logger.debug("{}, client pool {}, set promise with client {}",
            options.getServiceName(), System.identityHashCode(this), client);
        promises.remove().set(client);
        return;
      } catch (IOException ex) {
        logger.error("createNewClient has IOException", ex);
        promises.remove().setException(ex);
        break;
      }
    }
  }

  private void setTimeout(final Promise<C> promise) {
    ScheduledFuture<Void> schedule = scheduledExecutor.schedule(new Callable<Void>() {
      @Override
      public Void call() {
        synchronized (BasicClientPool.this) {
          promise.setException(new ClientPoolException("Timeout acquiring client"));
        }
        return VOID;
      }
    }, getPromiseTimeoutMs(), TimeUnit.MILLISECONDS);
    promise.setTimeout(schedule);
  }

  private boolean canCreateClient() {
    return clientTransportMap.size() < options.getMaxClients() && availableServers.length > 0;
  }

  private C createNewClient() throws IOException {
    logger.debug("start createNewClient");
    int randomIndex = random.nextInt(availableServers.length);
    InetSocketAddress address = availableServers[randomIndex];
    C client = ClientPoolUtils.createNewClient(address, this.protocolFactory,
        this.options, this.thriftFactory, this.clientFactory, this.clientTransportMap);
    logger.debug("createNewClient return client {}", client);
    return client;
  }

  private long getPromiseTimeoutMs() {
    return options.getTimeoutMs() * Math.max(1, promises.size());
  }
}
