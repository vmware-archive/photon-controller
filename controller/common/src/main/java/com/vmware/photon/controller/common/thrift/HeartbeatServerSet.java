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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.PingRequest;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A heartbeat-based server set. This implementation of ServerSet monitors a set
 * of scheduler servers by pinging them periodically. This class is meant to be
 * used by the root scheduler to monitor its children.
 */
public class HeartbeatServerSet implements ServerSet {
  private static final Logger logger =
      LoggerFactory.getLogger(HeartbeatServerSet.class);

  // ID of this scheduler.
  private final String schedulerId;
  private final int heartbeatFrequencyMs;
  private final int socketTimeoutMs;

  private final ScheduledExecutorService executor;

  // Access to listeners and activeServers must be synchronized to ensure that
  // the listeners don't miss any update.
  private final Set<ChangeListener> listeners = new HashSet<>();
  ;
  private final Set<InetSocketAddress> activeServers = new HashSet<>();
  private final Set<InetSocketAddress> inactiveServers = new HashSet<>();

  @Inject
  public HeartbeatServerSet(@Assisted String schedulerId,
                            @Assisted List<InetSocketAddress> servers,
                            @Assisted int timeoutMs,
                            @Named("heartbeat_pool_size") int poolSize) {
    this.schedulerId = schedulerId;
    // A server is considered inactive if it doesn't respond to a heartbeat
    // request for $timeoutMs milliseconds. The accepted range for $timeoutMs
    // is between 1000 and 120000 milliseconds.
    socketTimeoutMs = timeoutMs / 3;
    heartbeatFrequencyMs = timeoutMs / 3;
    executor = Executors.newScheduledThreadPool(poolSize);
    long initialDelayMs = 0;
    for (InetSocketAddress server : servers) {
      this.executor.scheduleAtFixedRate(new Heartbeater(server),
          initialDelayMs, heartbeatFrequencyMs, MILLISECONDS);
    }
  }

  @Override
  public synchronized void addChangeListener(ChangeListener listener) {
    logger.debug("adding a listener {}", listener);
    listeners.add(listener);
    // tell this newly added listener about currently active and inactive
    // servers.
    for (InetSocketAddress server : activeServers) {
      listener.onServerAdded(server);
    }
    for (InetSocketAddress server : inactiveServers) {
      listener.onServerRemoved(server);
    }
  }

  @Override
  public synchronized void removeChangeListener(ChangeListener listener) {
    logger.debug("removing a listener {}", listener);
    listeners.remove(listener);
  }

  @Override
  public void close() throws IOException {
    executor.shutdown();
  }

  @Override
  public Set<InetSocketAddress> getServers() {
    Set<InetSocketAddress> allServers = new HashSet<InetSocketAddress>();
    allServers.addAll(this.activeServers);
    allServers.addAll(this.inactiveServers);
    return allServers;
  }

  private class Heartbeater implements Runnable {
    private final InetSocketAddress server;

    public Heartbeater(InetSocketAddress server) {
      this.server = server;
    }

    public void run() {
      TTransport transport = null;
      try {
        logger.debug("pinging {}", server);
        TSocket socket = new TSocket(server.getHostString(), server.getPort());
        socket.setTimeout(socketTimeoutMs);
        transport = new TFramedTransport(socket);
        transport.open();
        TProtocol proto = new TCompactProtocol(transport);
        TMultiplexedProtocol mp = new TMultiplexedProtocol(proto, "AgentControl");
        AgentControl.Client client = new AgentControl.Client(mp);

        PingRequest req = new PingRequest();
        req.setScheduler_id(schedulerId);
        client.ping(req);
        pingSucceeded();
      } catch (TException ex) {
        // TODO(mmutsuzaki) temporarily bumping log level to INFO to find out
        // why ping requests fail under load.
        logger.info("failed to ping {}", server, ex);
        pingFailed();
      } catch (Exception ex) {
        logger.error("Unexpected exception while pinging {}", server, ex);
        pingFailed();
      } finally {
        if (transport != null && transport.isOpen()) {
          transport.close();
        }
      }
    }

    public void pingSucceeded() {
      // Synchronize on the outer class since this method accesses fields from
      // the outer class.
      synchronized (HeartbeatServerSet.this) {
        if (!activeServers.contains(server)) {
          logger.debug("{} came online", server);
          activeServers.add(server);
          inactiveServers.remove(server);
          for (ChangeListener listener : listeners) {
            listener.onServerAdded(server);
          }
        }
      }
    }

    public void pingFailed() {
      // Synchronize on the outer class since this method accesses fields from
      // the outer class.
      synchronized (HeartbeatServerSet.this) {
        if (!inactiveServers.contains(server)) {
          logger.debug("{} went offline", server);
          inactiveServers.add(server);
          activeServers.remove(server);
          for (ChangeListener listener : listeners) {
            listener.onServerRemoved(server);
          }
        }
      }
    }
  }
}
