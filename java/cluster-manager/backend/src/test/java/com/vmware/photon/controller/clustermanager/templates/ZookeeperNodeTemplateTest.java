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

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements tests for {@link ZookeeperNodeTemplate}.
 */
public class ZookeeperNodeTemplateTest {

  private ZookeeperNodeTemplate template = new ZookeeperNodeTemplate();

  private static final String DNS = "10.10.10.10";
  private static final String GATEWAY = "11.11.11.11";
  private static final String NETMASK = "255.255.255.0";
  private static final String ZOOKEEPER_PARAMETERS =
      "-e ADDITIONAL_ZOOKEEPER_1=server.1=10.0.0.1:2888:3888 " +
      "-e ADDITIONAL_ZOOKEEPER_2=server.2=10.0.0.2:2888:3888 " +
      "-e ADDITIONAL_ZOOKEEPER_3=server.3=10.0.0.3:2888:3888 " +
      "-e ADDITIONAL_ZOOKEEPER_4=server.4=10.0.0.4:2888:3888 ";

  private Map<String, String> createCloudConfigProperties(int nodeIndex) {
    List<String> addresses = createZookeeperAddresses();
    Map<String, String> nodeProperties = ZookeeperNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, addresses);
    nodeProperties.put(NodeTemplateUtils.NODE_INDEX_PROPERTY, Integer.toString(nodeIndex));
    return nodeProperties;
  }

  private List<String> createZookeeperAddresses() {
    List<String> addresses = new ArrayList<>();
    addresses.add("10.0.0.1");
    addresses.add("10.0.0.2");
    addresses.add("10.0.0.3");
    addresses.add("10.0.0.4");

    return addresses;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests getVmName method.
   */
  public class GetVmNameTest {

    @Test
    public void testGetVmName() {
      Map<String, String> nodeProperties = new HashMap<>();
      String hostId = UUID.randomUUID().toString();
      nodeProperties.put(NodeTemplateUtils.HOST_ID_PROPERTY, hostId);

      String name = template.getVmName(nodeProperties);
      assertEquals(name, "zookeeper-" + hostId);
    }
  }

  /**
   * Tests createUserDataTemplate method.
   */
  public class CreateUserDataTemplateTest {

    @Test
    public void testSuccess() {
      Map<String, String> properties = createCloudConfigProperties(1);
      FileTemplate userData = template.createUserDataTemplate("temp", properties);

      assertNotNull(userData);
      assertNotNull(userData.filePath);
      assertNotNull(userData.parameters);

      String dns = userData.parameters.get("$DNS");
      assertEquals(dns, "DNS=" + DNS);

      String gateway = userData.parameters.get("$GATEWAY");
      assertEquals(gateway, GATEWAY);

      String serverId = userData.parameters.get("$ZK_ID");
      assertEquals(serverId, "2");

      String address = userData.parameters.get("$ADDRESS");
      assertEquals(address, "10.0.0.2/24");

      String zookeeperParameters = userData.parameters.get("$ZK_PARAMETERS");
      assertEquals(zookeeperParameters, ZOOKEEPER_PARAMETERS);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullScriptDirectory() {
      template.createUserDataTemplate(null, createCloudConfigProperties(2));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullProperties() {
      template.createUserDataTemplate("temp", null);
    }
  }

  /**
   * Tests createMetaDataTemplate method.
   */
  public class CreateMetaDataTemplateTest {

    @Test
    public void testSuccess() {
      FileTemplate metaData = template.createMetaDataTemplate("temp", createCloudConfigProperties(1));

      assertNotNull(metaData);
      assertNotNull(metaData.filePath);
      String instanceId = metaData.parameters.get("$INSTANCE_ID");
      assertTrue(instanceId.startsWith(template.VM_NAME_PREFIX));

      String hostname = metaData.parameters.get("$LOCAL_HOSTNAME");
      assertTrue(hostname.startsWith(template.VM_NAME_PREFIX));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullScriptDirectory() {
      template.createMetaDataTemplate(null, createCloudConfigProperties(1));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullProperites() {
      template.createMetaDataTemplate("temp", null);
    }
  }

  /**
   * Tests createProperties method.
   */
  public class CreatePropertiesTest {

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullDns() {
      ZookeeperNodeTemplate.createProperties(null, GATEWAY, NETMASK, createZookeeperAddresses());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGateway() {
      ZookeeperNodeTemplate.createProperties(DNS, null, NETMASK, createZookeeperAddresses());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNetmask() {
      ZookeeperNodeTemplate.createProperties(DNS, GATEWAY, null, createZookeeperAddresses());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullZookeeperAddresses() {
      ZookeeperNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, null);
    }
  }
}
