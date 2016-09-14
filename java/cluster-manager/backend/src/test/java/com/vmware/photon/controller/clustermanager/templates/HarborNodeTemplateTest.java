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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implements tests for {@link HarborNodeTemplate}.
 */
public class HarborNodeTemplateTest {

  private HarborNodeTemplate template = new HarborNodeTemplate();

  private static final String DNS = "10.10.10.10";
  private static final String GATEWAY = "11.11.11.11";
  private static final String NETMASK = "255.255.255.0";
  private static final String IP_ADDRESS = "10.0.0.1";
  private static final String SSH_KEY = "test-key";
  private static final String ADMIN_PASSWORD = "admin-password";

  private Map<String, String> createCloudConfigProperties(int nodeIndex) {
    Map<String, String> nodeProperties = HarborNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, IP_ADDRESS,
        SSH_KEY, ADMIN_PASSWORD);
    nodeProperties.put(NodeTemplateUtils.NODE_INDEX_PROPERTY, Integer.toString(nodeIndex));
    return nodeProperties;
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
      assertEquals(name, "harbor-" + hostId);
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

      String address = userData.parameters.get("$ADDRESS");
      assertEquals(address, "10.0.0.1/24");

      String harborPort = userData.parameters.get("$HARBOR_PORT");
      assertEquals(harborPort, String.valueOf(ClusterManagerConstants.Harbor.HARBOR_PORT));
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
      HarborNodeTemplate.createProperties(null, GATEWAY, NETMASK, IP_ADDRESS, SSH_KEY, ADMIN_PASSWORD);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGateway() {
      HarborNodeTemplate.createProperties(DNS, null, NETMASK, IP_ADDRESS, SSH_KEY, ADMIN_PASSWORD);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNetmask() {
      HarborNodeTemplate.createProperties(DNS, GATEWAY, null, IP_ADDRESS, SSH_KEY, ADMIN_PASSWORD);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullHarborAddresses() {
      HarborNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, null, SSH_KEY, ADMIN_PASSWORD);
    }

    @Test
    public void testNullSshKey() {
      HarborNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, IP_ADDRESS, null, ADMIN_PASSWORD);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullAdminPassword() {
      HarborNodeTemplate.createProperties(DNS, GATEWAY, NETMASK, IP_ADDRESS, SSH_KEY, null);
    }
  }
}
