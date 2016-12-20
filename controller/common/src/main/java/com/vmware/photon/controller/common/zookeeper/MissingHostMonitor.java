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

package com.vmware.photon.controller.common.zookeeper;


/**
 * MissingHostMonitor interface represents a monitor that will trigger call backs on listeners
 * that are interested in receiving missing host add/removed events.
 */
public interface MissingHostMonitor {

  /**
   * Registers a change listener to a missing host monitor. When a change listener is added
   * an onHostAdded call back will be triggered for all hosts that are currently added.
   *
   * @param listener change listener
   */
  void addChangeListener(ChangeListener listener);

  /**
   * Removes a registered change listener from a missing host monitor.
   *
   * @param listener change listener
   */
  void removeChangeListener(ChangeListener listener);

  /**
   * Clients interested in getting notifications about missing host changes should implement
   * this interface.
   */
  interface ChangeListener {
    /**
     * Callback that gets called when a new missing host gets added.
     *
     * @param id host id
     */
    void onHostAdded(String id);

    /**
     * Callback that gets called when a missing host is removed.
     *
     * @param id host id
     */
    void onHostRemoved(String id);
  }
}
