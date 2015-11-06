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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * ServerSet interface represents a set of servers. It notifies its listeners when new servers get
 * added or old ones get removed.
 */
public interface ServerSet extends Closeable {

  /**
   * Registers a change listener with a server set.
   *
   * @param listener change listener
   */
  void addChangeListener(ChangeListener listener);

  /**
   * Removes a registered change listener from a server set.
   *
   * @param listener change listener
   */
  void removeChangeListener(ChangeListener listener);

  /**
   * CLoses the server set. Using server set after closing is an undefined behavior.
   *
   * @throws IOException
   */
  void close() throws IOException;

  /**
   * Returns the set of all the servers.
   */
  Set<InetSocketAddress> getServers();

  /**
   * Clients interested in getting notifications about ServerSet changes should implement this interface.
   */
  interface ChangeListener {
    /**
     * Callback that gets called when new server gets added to a set.
     *
     * @param address new server address
     */
    void onServerAdded(InetSocketAddress address);

    /**
     * Callback that gets called when a server is removed from a set.
     *
     * @param address removed server address
     */
    void onServerRemoved(InetSocketAddress address);
  }
}
