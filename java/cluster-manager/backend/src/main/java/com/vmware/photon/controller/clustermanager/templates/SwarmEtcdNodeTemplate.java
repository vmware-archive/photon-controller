/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import org.apache.commons.net.util.SubnetUtils;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the template for Swarm Etcd Nodes.
 */
public class SwarmEtcdNodeTemplate implements NodeTemplate {

  public static final String ETCD_USER_DATA_TEMPLATE = "swarm-etcd-user-data.template";
  public static final String DNS_PROPERTY = "dns";
  public static final String GATEWAY_PROPERTY = "gateway";
  public static final String NETMASK_PROPERTY = "netmask";
  public static final String ETCD_IPS_PROPERTY = "etcdIps";
  public static final String VM_NAME_PREFIX = "etcd";

  public String getVmName(Map<String, String> properties) {
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String hostId = properties.get(NodeTemplateUtils.HOST_ID_PROPERTY);
    return NodeTemplateUtils.generateHostName(VM_NAME_PREFIX, hostId);
  }

  public FileTemplate createUserDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String dns = properties.get(DNS_PROPERTY);
    String gateway = properties.get(GATEWAY_PROPERTY);
    String netmask = properties.get(NETMASK_PROPERTY);
    String nodeIndexStr = properties.get(NodeTemplateUtils.NODE_INDEX_PROPERTY);

    int nodeIndex = Integer.parseInt(nodeIndexStr);
    List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(properties.get(ETCD_IPS_PROPERTY));

    String ipAddress = etcdIps.get(nodeIndex);
    String cidrSignature = new SubnetUtils(ipAddress, netmask).getInfo().getCidrSignature();
    String etcdParameters = createEtcdParameters(etcdIps);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("$DNS", "DNS=" + dns);
    parameters.put("$GATEWAY", gateway);
    parameters.put("$ETCD_ID", Integer.toString(nodeIndex));
    parameters.put("$ADDRESS", cidrSignature);
    parameters.put("$ETCD_PARAMETERS", etcdParameters);
    parameters.put("$ETCD_PORT", Integer.toString(ClusterManagerConstants.Swarm.ETCD_PORT));
    parameters.put("$ETCD_PEER_PORT", Integer.toString(ClusterManagerConstants.Swarm.ETCD_PEER_PORT));

    FileTemplate template = new FileTemplate();
    template.filePath = Paths.get(scriptDirectory, ETCD_USER_DATA_TEMPLATE).toString();
    template.parameters = parameters;
    return template;
  }

  public FileTemplate createMetaDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    return NodeTemplateUtils.createMetaDataTemplate(scriptDirectory, getVmName(properties));
  }

  public static Map<String, String> createProperties(
      String dns, String gateway, String netmask, List<String> etcdAddresses) {

    Preconditions.checkNotNull(dns, "dns cannot be null");
    Preconditions.checkNotNull(gateway, "gateway cannot be null");
    Preconditions.checkNotNull(netmask, "netmask cannot be null");
    Preconditions.checkNotNull(etcdAddresses, "etcdAddresses cannot be null");
    Preconditions.checkArgument(etcdAddresses.size() > 0, "etcdAddresses should contain at least one address");

    Map<String, String> properties = new HashMap<>();
    properties.put(DNS_PROPERTY, dns);
    properties.put(GATEWAY_PROPERTY, gateway);
    properties.put(NETMASK_PROPERTY, netmask);
    properties.put(ETCD_IPS_PROPERTY, NodeTemplateUtils.serializeAddressList(etcdAddresses));

    return properties;
  }

  private static String createEtcdParameters(List<String> etcdIps) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < etcdIps.size(); i++) {
      sb.append("etcd" + i + "=http://" + etcdIps.get(i) + ":" + ClusterManagerConstants.Swarm.ETCD_PEER_PORT);
      if (i != etcdIps.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }
}
