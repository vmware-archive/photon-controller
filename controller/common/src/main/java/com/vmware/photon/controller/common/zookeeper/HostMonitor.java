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
 * HostMonitor interface represents a monitor that will trigger call backs on listeners
 * that are interested in receiving host add/updated/removed events.
 */
public interface HostMonitor {

  /**
   * Registers a change listener to a host monitor. When a change listener is added
   * an onHostAdded call back will be triggered for all hosts that are currently added.
   *
   * @param listener change listener
   */
  void addChangeListener(HostChangeListener listener);

  /**
   * Removes a registered change listener from a host monitor.
   *
   * @param listener change listener
   */
  void removeChangeListener(HostChangeListener listener);

}
