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
import org.apache.commons.net.util.SubnetUtils;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the template for Kubernetes Master Nodes.
 */
public class KubernetesMasterNodeTemplate implements NodeTemplate {

  public static final String MASTER_USER_DATA_TEMPLATE = "kubernetes-master-user-data.template";
  public static final String ETCD_IPS_PROPERTY = "etcdIps";
  public static final String DNS_PROPERTY = "dns";
  public static final String GATEWAY_PROPERTY = "gateway";
  public static final String MASTER_IP_PROPERTY = "masterIp";
  public static final String NETMASK_PROPERTY = "netmask";
  public static final String CONTAINER_NETWORK_PROPERTY = "containerNetwork";
  public static final String VM_NAME_PREFIX = "master";
  public static final String SSH_KEY_PROPERTY = "sshKey";
  public static final String CA_CERT_PROPERTY = "caCert";

  public String getVmName(Map<String, String> properties) {
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String hostId = properties.get(NodeTemplateUtils.HOST_ID_PROPERTY);
    return NodeTemplateUtils.generateHostName(VM_NAME_PREFIX, hostId);
  }

  public FileTemplate createUserDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(properties.get(ETCD_IPS_PROPERTY));
    String ipAddress = properties.get(MASTER_IP_PROPERTY);
    String netmask = properties.get(NETMASK_PROPERTY);
    String cidrSignature = new SubnetUtils(ipAddress, netmask).getInfo().getCidrSignature();
    String sshKey = properties.get(SSH_KEY_PROPERTY);
    String caCert = properties.get(CA_CERT_PROPERTY);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("$ETCD_QUORUM", NodeTemplateUtils.createEtcdQuorumString(etcdIps));
    parameters.put("$DNS", "DNS=" + properties.get(DNS_PROPERTY));
    parameters.put("$GATEWAY", properties.get(GATEWAY_PROPERTY));
    parameters.put("$ADDRESS", cidrSignature);
    parameters.put("$CONTAINER_NETWORK", properties.get(CONTAINER_NETWORK_PROPERTY));
    parameters.put("$KUBERNETES_PORT", String.valueOf(ClusterManagerConstants.Kubernetes.API_PORT));
    parameters.put("$LOCAL_HOSTNAME", getVmName(properties));
    parameters.put("$SSH_KEY", sshKey);
    parameters.put("$CA_CERT", fixIndentationForCertificate(caCert));

    FileTemplate template = new FileTemplate();
    template.filePath = Paths.get(scriptDirectory, MASTER_USER_DATA_TEMPLATE).toString();
    template.parameters = parameters;
    return template;
  }

  public FileTemplate createMetaDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    return NodeTemplateUtils.createMetaDataTemplate(scriptDirectory, getVmName(properties));
  }

  public static Map<String, String> createProperties(
      List<String> etcdAddresses, String dns, String gateway, String netmask,
      String masterIp, String containerNetwork, String sshKey, String caCert) {
    Preconditions.checkNotNull(etcdAddresses, "etcdAddresses cannot be null");
    Preconditions.checkArgument(etcdAddresses.size() > 0, "etcdAddresses should contain at least one address");
    Preconditions.checkNotNull(dns, "dns cannot be null");
    Preconditions.checkNotNull(gateway, "gateway cannot be null");
    Preconditions.checkNotNull(masterIp, "masterIp cannot be null");
    Preconditions.checkNotNull(netmask, "netmask cannot be null");
    Preconditions.checkNotNull(containerNetwork, "containerNetwork cannot be null");

    Map<String, String> properties = new HashMap<>();
    properties.put(ETCD_IPS_PROPERTY, NodeTemplateUtils.serializeAddressList(etcdAddresses));
    properties.put(DNS_PROPERTY, dns);
    properties.put(GATEWAY_PROPERTY, gateway);
    properties.put(MASTER_IP_PROPERTY, masterIp);
    properties.put(NETMASK_PROPERTY, netmask);
    properties.put(CONTAINER_NETWORK_PROPERTY, containerNetwork);
    properties.put(SSH_KEY_PROPERTY, sshKey);
    properties.put(CA_CERT_PROPERTY, caCert);

    return properties;
  }

  /**
   * The CA certificate is multi-line. Cloud-init requires multiline files to be indented as per yaml format.
   * So this adds the indentation with required number of spaces.
   */
  public static String fixIndentationForCertificate(String caCert) {
    if (caCert != null) {
      return caCert.replaceAll("\n", "\n        ");
    } else {
      return null;
    }
  }
}
