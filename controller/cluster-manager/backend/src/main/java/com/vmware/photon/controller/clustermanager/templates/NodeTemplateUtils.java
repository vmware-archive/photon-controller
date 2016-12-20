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

import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.FileTemplate;

import com.google.common.base.Preconditions;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class that provides helper methods for generating node-templates.
 */
public class NodeTemplateUtils {

  public static final String META_DATA_TEMPLATE = "meta-data.template";
  public static final String NODE_INDEX_PROPERTY = "nodeIndex";
  public static final String HOST_ID_PROPERTY = "hostId";

  /**
   * Creates the cloud-config's meta-data file template.
   */
  public static FileTemplate createMetaDataTemplate(String scriptDirectory, String hostname) {
    Map<String, String> parameters = new HashMap<>();
    parameters.put("$INSTANCE_ID", hostname);
    parameters.put("$LOCAL_HOSTNAME", hostname);

    FileTemplate template = new FileTemplate();
    template.filePath = Paths.get(scriptDirectory, META_DATA_TEMPLATE).toString();
    template.parameters = parameters;
    return template;
  }

  /**
   * Generates the hostname for a node.
   */
  public static String generateHostName(String vmName, String hostId) {
    String hostname = vmName + '-' + hostId;
    Preconditions.checkState(hostname.equals(hostname.toLowerCase()),
        "hostname should not contain upper case characters");
    return hostname;
  }

  /**
   * Serialize a list of IP addresses to a string.
   */
  public static String serializeAddressList(List<String> addressList) {
    StringBuilder sb = new StringBuilder();
    for (String s : addressList) {
      if (sb.length() != 0) {
        sb.append(",");
      }
      sb.append(s);
    }
    return sb.toString();
  }

  /**
   * Deserialize a list of IP addresses from a string.
   */
  public static List<String> deserializeAddressList(String serializedAddresses) {
    return Arrays.asList(serializedAddresses.split(","));
  }

  /**
   * Creates the Zookeeper node quorum string.
   */
  public static String createZookeeperQuorumString(List<String> zookeeperIps) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < zookeeperIps.size(); i++) {
      sb.append(zookeeperIps.get(i) + ":" + ClusterManagerConstants.Mesos.ZOOKEEPER_PORT);
      if (i != zookeeperIps.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

  /**
   * Creates the Etcd node quorum string.
   */
  public static String createEtcdQuorumString(List<String> etcdIps) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < etcdIps.size(); i++) {
      sb.append(etcdIps.get(i));
      if (i != etcdIps.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

  /**
   * Creates the Etcd node quorum string with a port for each Etcd node.
   */
  public static String createEtcdQuorumWithPortsString(List<String> etcdIps) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < etcdIps.size(); i++) {
      sb.append(etcdIps.get(i) + ":" + ClusterManagerConstants.Swarm.ETCD_PORT);
      if (i != etcdIps.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }
}
