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
 * Implements tests for {@link KubernetesMasterNodeTemplate}.
 */
public class KubernetesMasterNodeTemplateTest {

  private KubernetesMasterNodeTemplate template = new KubernetesMasterNodeTemplate();

  private static final String DNS = "10.10.10.10";
  private static final String GATEWAY = "11.11.11.11";
  private static final String MASTERIP = "10.1.2.3";
  private static final String NETMASK = "255.255.255.0";
  private static final String CONTAINER_NETWORK = "1.1.1.1/16";
  private static final String ETCD_QUORUM_STRING = "10.0.0.1,10.0.0.2,10.0.0.3,10.0.0.4";
  private static final String SSH_KEY = "test-key";
  private static final String CA_CERT = "example-ca-cert";


  private Map<String, String> createCloudConfigProperties() {
    return KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
        DNS, GATEWAY, NETMASK, MASTERIP, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
  }

  private List<String> createEtcdAddresses() {
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
      assertEquals(name, "master-" + hostId);
    }
  }

  /**
   * Tests createUserDataTemplate method.
   */
  public class CreateUserDataTemplateTest {

    @Test
    public void testSuccess() {
      FileTemplate userData = template.createUserDataTemplate("temp", createCloudConfigProperties());

      assertNotNull(userData);
      assertNotNull(userData.filePath);
      assertNotNull(userData.parameters);

      String etcdQuorum = userData.parameters.get("$ETCD_QUORUM");
      assertEquals(etcdQuorum, ETCD_QUORUM_STRING);

      String dns = userData.parameters.get("$DNS");
      assertEquals(dns, "DNS=" + DNS);

      String gateway = userData.parameters.get("$GATEWAY");
      assertEquals(gateway, GATEWAY);

      String address = userData.parameters.get("$ADDRESS");
      assertEquals(address, "10.1.2.3/24");

      String containerNetwork = userData.parameters.get("$CONTAINER_NETWORK");
      assertEquals(containerNetwork, CONTAINER_NETWORK);

      String kubernetesPort = userData.parameters.get("$KUBERNETES_PORT");
      assertEquals(kubernetesPort, Integer.toString(ClusterManagerConstants.Kubernetes.API_PORT));

      String sshKey = userData.parameters.get("$SSH_KEY");
      assertEquals(sshKey, SSH_KEY);

      String caCert = userData.parameters.get("$CA_CERT");
      assertEquals(caCert, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullScriptDirectory() {
      template.createUserDataTemplate(null, createCloudConfigProperties());
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
      FileTemplate metaData = template.createMetaDataTemplate("temp", createCloudConfigProperties());

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
      template.createMetaDataTemplate(null, createCloudConfigProperties());
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmpltyEtcdAddresses() {
      KubernetesMasterNodeTemplate.createProperties(new ArrayList<>(),
          null, GATEWAY, NETMASK, MASTERIP, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullDns() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          null, GATEWAY, NETMASK, MASTERIP, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGateway() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          DNS, null, NETMASK, MASTERIP, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNetmask() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          DNS, GATEWAY, null, MASTERIP, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullMasterIp() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          DNS, GATEWAY, NETMASK, null, CONTAINER_NETWORK, SSH_KEY, CA_CERT);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullContainerNetwork() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          DNS, GATEWAY, NETMASK, MASTERIP, null, SSH_KEY, CA_CERT);
    }

    @Test
    public void testNullSshKey() {
      KubernetesMasterNodeTemplate.createProperties(createEtcdAddresses(),
          DNS, GATEWAY, NETMASK, MASTERIP, CONTAINER_NETWORK, null, CA_CERT);
    }
  }
}
