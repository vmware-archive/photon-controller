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

  public static final String MASTER_VM_FLAVOR                           = "cluster-master-vm";
  public static final String OTHER_VM_FLAVOR                            = "cluster-other-vm";
  public static final String VM_DISK_FLAVOR                             = "cluster-vm-disk";
  public static final String EXTENDED_PROPERTY_DNS                      = "dns";
  public static final String EXTENDED_PROPERTY_GATEWAY                  = "gateway";
  public static final String EXTENDED_PROPERTY_NETMASK                  = "netmask";
  public static final String EXTENDED_PROPERTY_MASTER_IP                = "master_ip";
  public static final String EXTENDED_PROPERTY_CONTAINER_NETWORK        = "container_network";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IPS            = "zookeeper_ips";
  public static final String EXTENDED_PROPERTY_ETCD_IPS                 = "etcd_ips";
  public static final String EXTENDED_PROPERTY_CLUSTER_VERSION          = "cluster_version";
  public static final String EXTENDED_PROPERTY_CLUSTER_UI_URL           = "cluster_ui_url";
  public static final String EXTENDED_PROPERTY_CLIENT_LINUX_AMD64_URL   = "client_linux_amd64_url";
  public static final String EXTENDED_PROPERTY_CLIENT_LINUX_386_URL     = "client_linux_386_url";
  public static final String EXTENDED_PROPERTY_CLIENT_DARWIN_AMD64_URL  = "client_darwin_amd64_url";
  public static final String EXTENDED_PROPERTY_CLIENT_WINDOWS_AMD64_URL = "client_windows_amd64_url";
  public static final String EXTENDED_PROPERTY_CLIENT_WINDOWS_386_URL   = "client_windows_386_url";
  public static final String EXTENDED_PROPERTY_SSH_KEY                  = "ssh_key";
  public static final String EXTENDED_PROPERTY_ADMIN_PASSWORD           = "admin_password";
  public static final String EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE  = "registry_ca_cert";
  public static final String EXTENDED_PROPERTY_CA_CERTIFICATE           = "ca_cert";


  public static final String KUBECTL_BASE_URI                           = "storage.googleapis.com";
  public static final String KUBECTL_PATH_RELEASE                       = "kubernetes-release/release";
  public static final String BIN                                        = "bin";
  public static final String KUBECTL                                    = "kubectl";
  public static final String KUBECTLEXE                                 = "kubectl.exe";

  public static final long DEFAULT_MAINTENANCE_INTERVAL = TimeUnit.HOURS.toMicros(1);
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

  /**
   * This class defines constant values for Harbor cluster.
   */
  public static class Harbor {
    public static final int HARBOR_PORT = 443;
  }
}
