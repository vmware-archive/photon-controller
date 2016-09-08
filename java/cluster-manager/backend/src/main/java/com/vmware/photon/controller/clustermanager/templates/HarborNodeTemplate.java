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
import java.util.Map;

/**
 * Defines the template for Harbor Master Nodes.
 */
public class HarborNodeTemplate implements NodeTemplate {

  public static final String USER_DATA_TEMPLATE = "harbor-user-data.template";
  public static final String DNS_PROPERTY = "dns";
  public static final String GATEWAY_PROPERTY = "gateway";
  public static final String IP_PROPERTY = "ip";
  public static final String NETMASK_PROPERTY = "netmask";
  public static final String VM_NAME_PREFIX = "harbor";

  public String getVmName(Map<String, String> properties) {
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String hostId = properties.get(NodeTemplateUtils.HOST_ID_PROPERTY);
    return NodeTemplateUtils.generateHostName(VM_NAME_PREFIX, hostId);
  }

  public FileTemplate createUserDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String ipAddress = properties.get(IP_PROPERTY);
    String netmask = properties.get(NETMASK_PROPERTY);
    String cidrSignature = new SubnetUtils(ipAddress, netmask).getInfo().getCidrSignature();

    Map<String, String> parameters = new HashMap<>();
    parameters.put("$DNS", "DNS=" + properties.get(DNS_PROPERTY));
    parameters.put("$GATEWAY", properties.get(GATEWAY_PROPERTY));
    parameters.put("$ADDRESS", cidrSignature);
    parameters.put("$HARBOR_PORT", String.valueOf(ClusterManagerConstants.Harbor.HARBOR_PORT));
    parameters.put("$LOCAL_HOSTNAME", getVmName(properties));

    FileTemplate template = new FileTemplate();
    template.filePath = Paths.get(scriptDirectory, USER_DATA_TEMPLATE).toString();
    template.parameters = parameters;
    return template;
  }

  public FileTemplate createMetaDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    return NodeTemplateUtils.createMetaDataTemplate(scriptDirectory, getVmName(properties));
  }

  public static Map<String, String> createProperties(
      String dns, String gateway, String netmask, String ip) {
    Preconditions.checkNotNull(dns, "dns cannot be null");
    Preconditions.checkNotNull(gateway, "gateway cannot be null");
    Preconditions.checkNotNull(ip, "ip cannot be null");
    Preconditions.checkNotNull(netmask, "netmask cannot be null");

    Map<String, String> properties = new HashMap<>();
    properties.put(DNS_PROPERTY, dns);
    properties.put(GATEWAY_PROPERTY, gateway);
    properties.put(IP_PROPERTY, ip);
    properties.put(NETMASK_PROPERTY, netmask);

    return properties;
  }
}
