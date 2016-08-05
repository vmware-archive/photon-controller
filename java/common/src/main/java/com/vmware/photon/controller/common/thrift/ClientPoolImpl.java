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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ClientPool}.
 *
 * @param <C> thrift async client type
 */
class ClientPoolImpl<C extends TAsyncClient> implements ClientPool<C>, ServerSet.ChangeListener {

  private static final Logger logger = LoggerFactory.getLogger(ClientPoolImpl.class);

  private final SecureRandom random;
  private final TAsyncClientFactory<C> clientFactory;
  private final TProtocolFactory protocolFactory;
  private final ThriftFactory thriftFactory;
  private final ScheduledExecutorService scheduledExecutor;
  private final ServerSet serverSet;
  private final ClientPoolOptions options;
  private final ListMultimap<InetSocketAddress, C> availableClients;
  private final Map<C, InetSocketAddress> acquiredClients;
  private final Set<InetSocketAddress> availableServers;
  private final Map<C, TTransport> clientTransportMap;
  private final Queue<Promise<C>> promises;
  private boolean closed;

  @Inject
  public ClientPoolImpl(SecureRandom random,
                        TAsyncClientFactory<C> clientFactory,
                        TProtocolFactory protocolFactory,
                        ThriftFactory thriftFactory,
                        @ClientPoolTimer ScheduledExecutorService scheduledExecutor,
                        @Assisted ServerSet serverSet,
                        @Assisted ClientPoolOptions options) {
    this.random = random;
    this.clientFactory = clientFactory;
    this.protocolFactory = protocolFactory;
    this.thriftFactory = thriftFactory;
    this.scheduledExecutor = scheduledExecutor;
    this.serverSet = serverSet;
    this.options = new ClientPoolOptions(options);
    this.availableClients = ArrayListMultimap.create();
    this.acquiredClients = new HashMap<>();
    this.availableServers = new HashSet<>();
    this.clientTransportMap = new HashMap<>();
    this.promises = new LinkedList<>();
    this.closed = false;

    // Must be last since it can start firing events immediately
    this.serverSet.addChangeListener(this);
  }

  public synchronized void onServerAdded(InetSocketAddress address) {
    logger.debug("Server {} added", address);
    availableServers.add(address);
    if (!availableClients.containsKey(address) &&
        canCreateClient()) {
      try {
        availableClients.put(address, createNewClient(address));
      } catch (Throwable ex) {
        logger.error("Error occurred when createNewClient for {}", address);
      }
    }
    processPromises();
  }

  public synchronized void onServerRemoved(InetSocketAddress address) {
    logger.debug("Server {} removed", address);
    availableServers.remove(address);
    List<C> clients = availableClients.removeAll(address);
    for (C client : clients) {
      removeClient(client);
    }
  }

  @Override
  public synchronized ListenableFuture<C> acquire() {
    if (promises.size() < options.getMaxWaiters()) {
      SettableFuture<C> future = SettableFuture.create();
      Promise<C> promise = new Promise<>(future);
      promises.add(promise);
      processPromises();
      logger.info("options.getTimeoutMs() is {}", options.getTimeoutMs());
      if (options.getTimeoutMs() > 0 && !future.isDone()) {
        setTimeout(promise);
      }
      logger.info("{} Promise processed {}", options.getServiceName(), promise);
      return future;
    }

    return Futures.immediateFailedFuture(new ClientPoolException("Too many waiters"));
  }

  private void setTimeout(final Promise<C> promise) {
    ScheduledFuture<Void> schedule = scheduledExecutor.schedule(new Callable<Void>() {
      @Override
      public Void call() {
        synchronized (ClientPoolImpl.this) {
          promise.setException(new ClientPoolException(String.format("Timeout acquiring client: %s", serverSet)));
        }
        return VOID;
      }
    }, getPromiseTimeoutMs(), TimeUnit.MILLISECONDS);
    promise.setTimeout(schedule);
    logger.info("Timeout set for the promise {}", getPromiseTimeoutMs());
  }

  @Override
  public synchronized void close() {
    logger.info("{}, closing client pool {}", options.getServiceName(), System.identityHashCode(this));
    Preconditions.checkState(!closed);

    serverSet.removeChangeListener(this);

    Iterator<Promise<C>> promiseIterator = promises.iterator();
    while (promiseIterator.hasNext()) {
      Promise<C> promise = promiseIterator.next();
      promise.setException(new ClientPoolException("Client pool closing"));
      promiseIterator.remove();
    }

    Iterator<C> clientIterator = availableClients.values().iterator();
    while (clientIterator.hasNext()) {
      C client = clientIterator.next();
      removeClient(client);
      clientIterator.remove();
    }

    availableClients.clear();
    closed = true;
  }

  @Override
  public synchronized boolean isClosed() {
    return this.closed;
  }

  @Override
  public synchronized void release(C client, boolean healthy) {
    logger.info("{}, client pool {}, releasing: {} healthy: {}",
        options.getServiceName(), System.identityHashCode(this), client, healthy);
    InetSocketAddress address = acquiredClients.remove(client);
    if (address == null) {
      throw new IllegalArgumentException("Client is not currently acquired by the pool: " + client);
    }

    if (!closed && healthy && availableServers.contains(address)) {
      availableClients.put(address, client);
    } else {
      logger.info("{}, client pool {}, closing transport for client: {}, healthy: {}",
          options.getServiceName(), System.identityHashCode(this), client, healthy);
      removeClient(client);
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
  protected ListMultimap<InetSocketAddress, C> getAvailableClients() {
    return availableClients;
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
      if (availableClients.isEmpty()) {
        logger.info("{}, no avail clients: acq {}, avail {}, promises {}",
            options.getServiceName(), acquiredClients.size(),
            availableClients.size(), promises.size());
      }

      if (!canCreateClient()) {
        logger.info("{}, client pool {}, not creating new client, request is queued until a client is available: " +
                "client(s) in use {}, request(s) waiting {}, available client(s) {}",
            options.getServiceName(), System.identityHashCode(this), acquiredClients.size(),
            promises.size(), availableClients.size());
      }

      C client = fulfillWithAvailableClient();
      if (client == null && canCreateClient()) {
        try {
          client = fulfillWithNewClient();
        } catch (Throwable ex) {
          logger.error("fulfillWithNewClient has exception", ex);
          promises.remove().setException(ex);
          break;
        }
      }

      if (client != null) {
        logger.info("{}, client pool {}, set promise with client {}",
            options.getServiceName(), System.identityHashCode(this), client);
        promises.remove().set(client);
        return;
      }

      break;
    }
  }

  private boolean canCreateClient() {
    return (availableClients.size() + acquiredClients.size()) < options.getMaxClients() &&
        !availableServers.isEmpty();
  }

  /**
   * Try to find an address that has more than one client.
   * Then we move the client from that address to the target address.
   *
   * @param address
   */
  private void moveClient(InetSocketAddress address) {
    logger.debug("Try to move a client to {}", address);
    C newClient = null;
    for (InetSocketAddress availableClientAddress : availableClients.keySet()) {
      if (availableClients.get(availableClientAddress).size() > 1) {
        logger.debug("Address {} has {} clients, move one to {}",
            availableClientAddress,
            availableClients.get(availableClientAddress).size(),
            address);

        try {
          newClient = createNewClient(address);
          removeClient(availableClients.get(availableClientAddress).remove(0));
          break;
        } catch (Throwable ex) {
          logger.error("moveClient: fail to create new client for {}", address);
          return;
        }
      }
    }

    if (newClient == null) {
      logger.debug("No client to move in {}", availableClients);
      return;
    }

    availableClients.put(address, newClient);
  }

  private void removeClient(C client) {
    logger.debug("remove client {}", client);
    TTransport transport = clientTransportMap.remove(client);
    transport.close();
  }

  private C fulfillWithAvailableClient() {
    logger.debug("start fulfillWithAvailableClient");
    // In case of an address does not have any available client,
    // we try to find other address that has more than one client
    // and move it so that that address will have one client instead
    // of none.
    for (InetSocketAddress server : availableServers) {
      if (!availableClients.containsKey(server)) {
        moveClient(server);
      }
    }

    C client = null;
    while (!availableClients.isEmpty() && client == null) {
      // The random selection occurs on InetSocketAddress, not clients
      Set<InetSocketAddress> keys = availableClients.keySet();
      InetSocketAddress[] addresses = keys.toArray(new InetSocketAddress[keys.size()]);
      int randomIndex = random.nextInt(addresses.length);
      InetSocketAddress randomAddress = addresses[randomIndex];
      client = reserveAvailableClient(randomAddress);
    }

    logger.debug("fulfillWithAvailableClient returns client {}", client);
    return client;
  }

  private C reserveAvailableClient(InetSocketAddress address) {
    C client = availableClients.get(address).remove(0);
    if (client.hasError()) {
      logger.warn("service {} available client {} has error",
          options.getServiceName(), client, client.getError());
      removeClient(client);
      return null;
    }

    acquiredClients.put(client, address);
    return client;
  }

  private C fulfillWithNewClient() throws IOException, TTransportException {
    logger.debug("start fulfillWithNewClient");
    InetSocketAddress[] servers = availableServers.toArray(new InetSocketAddress[availableServers.size()]);
    int randomIndex = random.nextInt(servers.length);
    InetSocketAddress address = servers[randomIndex];
    C client = createNewClient(address);
    acquiredClients.put(client, address);
    logger.debug("fulfillWithNewClient return client {}", client);
    return client;
  }

  private C createNewClient(InetSocketAddress address) throws IOException, TTransportException {
    return ClientPoolUtils.createNewClient(address, this.protocolFactory,
        this.options, this.thriftFactory, this.clientFactory, this.clientTransportMap);
  }

  private long getPromiseTimeoutMs() {
    return options.getTimeoutMs() * Math.max(1, promises.size());
  }
}
