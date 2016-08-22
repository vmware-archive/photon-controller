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
 * Implements tests for {@link SwarmWorkerNodeTemplate}.
 */
public class SwarmWorkerNodeTemplateTest {

  private SwarmWorkerNodeTemplate template = new SwarmWorkerNodeTemplate();

  private static final String ETCD_QUORUM_STRING =
      "10.0.0.1:2379,10.0.0.2:2379,10.0.0.3:2379,10.0.0.4:2379";

  private Map<String, String> createCloudConfigProperties() {
    return SwarmWorkerNodeTemplate.createProperties(createEtcdAddresses());
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
      assertEquals(name, "worker-" + hostId);
    }
  }

  /**
   * Tests createUserDataTemplate method.
   */
  public class CreateUserDataTemplateTest {

    @Test
    public void testSuccess() {
      Map<String, String> properties = createCloudConfigProperties();
      FileTemplate userData = template.createUserDataTemplate("temp", properties);

      assertNotNull(userData);
      assertNotNull(userData.filePath);
      assertNotNull(userData.parameters);

      String etcdQuorum = userData.parameters.get("$ETCD_QUORUM");
      assertEquals(etcdQuorum, ETCD_QUORUM_STRING);
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

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullEtcdAddresses() {
      SwarmWorkerNodeTemplate.createProperties(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmpltyEtcdAddresses() {
      SwarmWorkerNodeTemplate.createProperties(new ArrayList<>());
    }
  }
}
