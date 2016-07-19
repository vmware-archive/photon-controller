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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test {@link HostEntity}.
 */
public class HostEntityTest {

  private HostState state;
  private String address;
  private String username;
  private String password;
  private String availabilityZone;
  private String esxVersion;
  private Map<String, String> metadata;
  private String usageTags;
  private List<HostDatastoreEntity> datastores;

  @BeforeMethod
  public void setup() {
    state = HostState.READY;
    address = "192.168.1.1";
    username = "username";
    password = "password";
    availabilityZone = "availabilityZone";
    esxVersion = "6.0";
    metadata = new HashMap<>();
    metadata.put("key1", "value1");
    usageTags = UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
      add(UsageTag.MGMT);
    }});

    HostDatastoreEntity datastore = new HostDatastoreEntity();
    datastore.setDatastoreId("id");
    datastore.setMountPoint("ds1");
    datastores = new ArrayList<>();
    datastores.add(datastore);
  }

  @Test
  public void testHostEntityGetterSetters() {
    HostEntity hostEntity = new HostEntity();

    hostEntity.setState(state);
    hostEntity.setAddress(address);
    hostEntity.setUsername(username);
    hostEntity.setPassword(password);
    hostEntity.setAvailabilityZone(availabilityZone);
    hostEntity.setEsxVersion(esxVersion);
    hostEntity.setMetadata(metadata);
    hostEntity.setUsageTags(usageTags);
    hostEntity.setDatastores(datastores);

    Assert.assertTrue(validateHostEntity(hostEntity));
  }

  private boolean validateHostEntity(HostEntity hostEntity) {
    return hostEntity.getState().equals(HostState.READY)
        && hostEntity.getAddress().equals(address)
        && hostEntity.getUsername().equals(username)
        && hostEntity.getPassword().equals(password)
        && hostEntity.getAvailabilityZone().equals(availabilityZone)
        && hostEntity.getEsxVersion().equals(esxVersion)
        && hostEntity.getMetadata().equals(metadata)
        && hostEntity.getUsageTags().equals(usageTags)
        && hostEntity.getDatastores().equals(datastores);
  }
}
