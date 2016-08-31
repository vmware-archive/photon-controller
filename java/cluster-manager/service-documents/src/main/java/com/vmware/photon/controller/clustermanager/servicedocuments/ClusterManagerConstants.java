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

package com.vmware.photon.controller.clustermanager.servicedocuments;

import java.util.concurrent.TimeUnit;

/**
 * This class defines constant values for cluster manager.
 */
public class ClusterManagerConstants {

  public static final String MASTER_VM_FLAVOR = "cluster-master-vm";
  public static final String OTHER_VM_FLAVOR = "cluster-other-vm";
  public static final String VM_DISK_FLAVOR = "cluster-vm-disk";

  public static final String EXTENDED_PROPERTY_DNS                   = "dns";
  public static final String EXTENDED_PROPERTY_GATEWAY               = "gateway";
  public static final String EXTENDED_PROPERTY_NETMASK               = "netmask";
  public static final String EXTENDED_PROPERTY_MASTER_IP             = "master_ip";
  public static final String EXTENDED_PROPERTY_CONTAINER_NETWORK     = "container_network";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IPS         = "zookeeper_ips";
  public static final String EXTENDED_PROPERTY_ETCD_IPS              = "etcd_ips";
  public static final String EXTENDED_PROPERTY_VERSION               = "version";
  public static final String EXTENDED_PROPERTY_UI_ADDRESS            = "uiAddress";
  public static final String EXTENDED_PROPERTY_LINUX_AMD64_ADDRESS   = "linuxAMD64Address";
  public static final String EXTENDED_PROPERTY_LINUX_386_ADDRESS     = "linux386Address";
  public static final String EXTENDED_PROPERTY_DARWIN_AMD64_ADDRESS  = "darwinAMD64Address";
  public static final String EXTENDED_PEOPERTY_WINDOWS_AMD64_ADDRESS = "windowsADM64Address";
  public static final String EXTENDED_PEROPERTY_WINDOWS_386_ADDRESS  = "windows386Address";


  public static final long DEFAULT_MAINTENANCE_INTERVAL = TimeUnit.HOURS.toMicros(1);
  public static final int DEFAULT_MAINTENANCE_RETRY_COUNT = 5;
  public static final int DEFAULT_MAINTENANCE_RETRY_INTERVAL_SECOND = 5;
  public static final int DEFAULT_TASK_POLL_DELAY = 1000;
  public static final int SCRIPT_TIMEOUT_IN_SECONDS = 600;

  public static final int DEFAULT_BATCH_EXPANSION_SIZE = 20;

  /**
   * This class defines constant values for Kubernetes cluster.
   */
  public static class Kubernetes {
    public static final int MASTER_COUNT = 1;

    public static final int API_PORT = 8080;
  }

  /**
   * This class defines constant values for Mesos cluster.
   */
  public static class Mesos {
    public static final int MASTER_COUNT = 3;
    public static final int MARATHON_COUNT = 1;

    public static final int ZOOKEEPER_PORT = 2181;
    public static final int MESOS_PORT = 5050;
    public static final int MARATHON_PORT = 8080;
  }

  /**
   * This class defines constant values for Swarm cluster.
   */
  public static class Swarm {
    public static final int MASTER_COUNT = 1;

    public static final int ETCD_PORT = 2379;
    public static final int ETCD_PEER_PORT = 2380;
    public static final int SWARM_PORT = 8333;
  }
}
