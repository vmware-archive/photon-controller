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

package com.vmware.photon.controller.clustermanager.statuschecks;

import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.xenon.common.Service;

import com.google.common.base.Preconditions;

/**
 * Factory class that is used to instantiate different StatusChecker.
 */
public class StatusCheckHelper {

  /**
   * Factory method used to create an instance of a StatusChecker corresponding to the passed NodeType.
   *
   * @param service  Reference to the Xenon Service that requires the StatusChecker instance.
   * @param nodeType Type of the Cluster Node.
   * @return
   */
  public StatusChecker createStatusChecker(Service service, NodeType nodeType) {
    Preconditions.checkNotNull(service);

    switch (nodeType) {
      case KubernetesMaster:
      case KubernetesSlave:
        return new KubernetesStatusChecker(HostUtils.getKubernetesClient(service));

      case MesosZookeeper:
        return new ZookeeperStatusChecker();

      case MesosMaster:
      case MesosSlave:
        return new MesosStatusChecker(HostUtils.getMesosClient(service));

      case MesosMarathon:
        return new MarathonStatusChecker(HostUtils.getMesosClient(service));

      case KubernetesEtcd:
      case SwarmEtcd:
        return new EtcdStatusChecker(HostUtils.getEtcdClient(service));

      case SwarmMaster:
      case SwarmSlave:
        return new SwarmStatusChecker(HostUtils.getSwarmClient(service));

      default:
        throw new RuntimeException("Unsupported nodeType: " + nodeType.toString());
    }
  }

  /**
   * Factory method used to create an instance of a StatusChecker corresponding to the passed NodeType.
   *
   * @param service  Reference to the Xenon Service that requires the StatusChecker instance.
   * @param nodeType Type of the Cluster Node.
   * @return
   */
  public SlavesStatusChecker createSlavesStatusChecker(Service service, NodeType nodeType) {
    Preconditions.checkNotNull(service);

    switch (nodeType) {
      case KubernetesSlave:
        return new KubernetesStatusChecker(HostUtils.getKubernetesClient(service));

      case MesosSlave:
        return new MesosStatusChecker(HostUtils.getMesosClient(service));

      case SwarmSlave:
        return new SwarmStatusChecker(HostUtils.getSwarmClient(service));

      default:
        throw new RuntimeException("Unsupported nodeType: " + nodeType.toString());
    }
  }
}
