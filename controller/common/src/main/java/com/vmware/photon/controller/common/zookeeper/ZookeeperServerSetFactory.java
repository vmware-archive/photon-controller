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

import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.inject.assistedinject.Assisted;

import javax.inject.Named;

/**
 * Creates server sets backed by ZooKeeper.
 */
public interface ZookeeperServerSetFactory {
  /**
   * Factory method to create a server set to monitor services. Services are typically management plane services that
   * hearbeat through zookeeper. E.g. scheduler.
   *
   * @param serviceName        The service name to monitor
   * @param subscribeToUpdates A true value will result in the the monitoring of the service entries on an ongoing
   *                           basis if false a one time callback is triggered for the nodes belonging to the service.
   * @return a ServerSet corresponding to the service
   */
  @Named("Service")
  ServerSet createServiceServerSet(@Assisted("serviceName") String serviceName, boolean subscribeToUpdates);

  /**
   * Factory method to monitor a particular host.
   *
   * @param hostName The host name to monitor
   * @return A ServerSet corresponding to the host being monitored.
   */
  @Named("Host")
  ServerSet createHostServerSet(@Assisted("hostName") String hostName);

}
