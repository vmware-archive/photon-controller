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

package com.vmware.photon.controller.clustermanager.templates;

import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;

/**
 * Defines the factory for creating node templates.
 */
public class NodeTemplateFactory {

  /**
   * Creates an instance of the Node template given the passed-in NodeType.
   */
  public static NodeTemplate createInstance(NodeType nodeType) {
    switch (nodeType) {
      case KubernetesEtcd:
        return new EtcdNodeTemplate();

      case KubernetesMaster:
        return new KubernetesMasterNodeTemplate();

      case KubernetesWorker:
        return new KubernetesWorkerNodeTemplate();

      case MesosZookeeper:
        return new ZookeeperNodeTemplate();

      case MesosMaster:
        return new MesosMasterNodeTemplate();

      case MesosWorker:
        return new MesosWorkerNodeTemplate();

      case MesosMarathon:
        return new MarathonNodeTemplate();

      case SwarmEtcd:
        return new EtcdNodeTemplate();

      case SwarmMaster:
        return new SwarmMasterNodeTemplate();

      case SwarmWorker:
        return new SwarmWorkerNodeTemplate();

      default:
        throw new RuntimeException("Unsupported nodeType: " + nodeType.toString());
    }
  }
}
