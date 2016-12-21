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

import com.vmware.photon.controller.clustermanager.servicedocuments.FileTemplate;

import com.google.common.base.Preconditions;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the template for Marathon Nodes.
 */
public class MarathonNodeTemplate implements NodeTemplate {

  public static final String MARATHON_USER_DATA_TEMPLATE = "mesos-marathon-user-data.template";
  public static final String ZOOKEEPER_IPS_PROPERTY = "zookeeperIps";
  public static final String VM_NAME_PREFIX = "marathon";

  public String getVmName(Map<String, String> properties) {
    Preconditions.checkNotNull(properties, "properties cannot be null");

    String hostId = properties.get(NodeTemplateUtils.HOST_ID_PROPERTY);
    return NodeTemplateUtils.generateHostName(VM_NAME_PREFIX, hostId);
  }

  public FileTemplate createUserDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    List<String> zookeeperIps = NodeTemplateUtils.deserializeAddressList(properties.get(ZOOKEEPER_IPS_PROPERTY));

    Map<String, String> parameters = new HashMap<>();
    parameters.put("$ZK_QUORUM", NodeTemplateUtils.createZookeeperQuorumString(zookeeperIps));
    parameters.put("$LOCAL_HOSTNAME", getVmName(properties));

    FileTemplate template = new FileTemplate();
    template.filePath = Paths.get(scriptDirectory, MARATHON_USER_DATA_TEMPLATE).toString();
    template.parameters = parameters;
    return template;
  }

  public FileTemplate createMetaDataTemplate(String scriptDirectory, Map<String, String> properties) {
    Preconditions.checkNotNull(scriptDirectory, "scriptDirectory cannot be null");
    Preconditions.checkNotNull(properties, "properties cannot be null");

    return NodeTemplateUtils.createMetaDataTemplate(scriptDirectory, getVmName(properties));
  }

  public static Map<String, String> createProperties(List<String> zkAddresses) {
    Preconditions.checkNotNull(zkAddresses, "zkAddresses cannot be null");
    Preconditions.checkArgument(zkAddresses.size() > 0, "zkAddresses should contain at least one address");

    Map<String, String> properties = new HashMap<>();
    properties.put(ZOOKEEPER_IPS_PROPERTY, NodeTemplateUtils.serializeAddressList(zkAddresses));

    return properties;
  }
}
