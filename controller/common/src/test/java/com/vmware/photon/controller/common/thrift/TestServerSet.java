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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Dummy ServerSet used for test.
 */
public class TestServerSet implements ServerSet {

  private final Set<ChangeListener> listeners;
  private final Set<InetSocketAddress> servers;

  public TestServerSet() {
    this.listeners = Collections.synchronizedSet(new HashSet<ChangeListener>());
    this.servers = new HashSet<>();
  }

  @Override
  public void addChangeListener(ChangeListener listener) {
    listeners.add(listener);
    for (InetSocketAddress server : servers) {
      listener.onServerAdded(server);
    }
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void close() throws IOException {
    // no-op
  }

  @Override
  public Set<InetSocketAddress> getServers() {
    return this.servers;
  }

  public void addServer(InetSocketAddress address) {
    servers.add(address);
    for (ChangeListener listener : listeners) {
      listener.onServerAdded(address);
    }
  }
}
