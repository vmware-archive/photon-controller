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
 * Implements tests for {@link KubernetesEtcdNodeTemplate}.
 */
public class KubernetesEtcdNodeTemplateTest {

  private KubernetesEtcdNodeTemplate template = new KubernetesEtcdNodeTemplate();

  private static final String DNS = "10.10.10.10";
  private static final String GATEWAY = "11.11.11.11";
  private static final String NETMASK = "255.255.255.0";
  private static final String ETCD_PARAMETERS =
      "etcd0=http://10.0.0.1:2380,etcd1=http://10.0.0.2:2380,etcd2=http://10.0.0.3:2380,etcd3=http://10.0.0.4:2380";
  private static final String SSH_KEY = "test-key";

  private Map<String, String> createCloudConfigProperties(int nodeIndex) {
    List<String> addresses = createEtcdAddresses();
    Map<String, String> nodeProperties = KubernetesEtcdNodeTemplate.createProperties(DNS, GATEWAY, NETMASK,
        addresses, SSH_KEY);
    nodeProperties.put(NodeTemplateUtils.NODE_INDEX_PROPERTY, Integer.toString(nodeIndex));
    return nodeProperties;
  }

  private List<String> createEtcdAddresses() {
    List<String> addresses = new ArrayList<>();
    addresses.add("10.0.0.1");
    addresses.add("10.0.0.2");
    addresses.add("10.0.0.3");
    addresses.add("10.0.0.4");

    return addresses;
  }

  @Test
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
      assertEquals(name, "etcd-" + hostId);
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

      String serverId = userData.parameters.get("$ETCD_ID");
      assertEquals(serverId, "1");

      String address = userData.parameters.get("$ADDRESS");
      assertEquals(address, "10.0.0.2/24");

      String etcdPort = userData.parameters.get("$ETCD_PORT");
      assertEquals(etcdPort, "2379");

      String etcdPeerPort = userData.parameters.get("$ETCD_PEER_PORT");
      assertEquals(etcdPeerPort, "2380");

      String etcdParameters = userData.parameters.get("$ETCD_PARAMETERS");
      assertEquals(etcdParameters, ETCD_PARAMETERS);

      String sshKey = userData.parameters.get("$SSH_KEY");
      assertEquals(sshKey, SSH_KEY);
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
      assertNotNull(metaData.parameters);

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
      KubernetesEtcdNodeTemplate.createProperties(null, GATEWAY, NETMASK, createEtcdAddresses(), SSH_KEY);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGateway() {
      KubernetesEtcdNodeTemplate.createProperties(DNS, null, NETMASK, createEtcdAddresses(), SSH_KEY);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNetmask() {
      KubernetesEtcdNodeTemplate.createProperties(DNS, GATEWAY, null, createEtcdAddresses(), SSH_KEY);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullEtcdAddresses() {
      KubernetesEtcdNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, null, SSH_KEY);
    }

    @Test
    public void testNullSshKey() {
      KubernetesEtcdNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, createEtcdAddresses(), null);
    }
  }
}
