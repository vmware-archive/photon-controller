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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.base.Infrastructure;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link PersistentDisk}.
 */
public class PersistentDiskTest {

  @Test
  public void testIsAnInfrastructure() throws Exception {
    assertThat(new PersistentDisk(), isA(Infrastructure.class));
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    private static final String JSON_FILE = "fixtures/persistent-disk.json";

    PersistentDisk disk;

    @BeforeMethod
    public void setUp() {
      disk = createValidDisk();
    }

    @Test
    public void testSerialization() throws IOException {
      MatcherAssert.assertThat(
          JsonHelpers.asJson(disk), sameJSONAs(JsonHelpers.jsonFixture(JSON_FILE)).allowingAnyArrayOrdering());

    }

    @Test
    public void testDeserialization() throws IOException {
      // clear members that are not serialized
      disk.setProjectId(null);

      assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(JSON_FILE), PersistentDisk.class), is(disk));
    }

    private PersistentDisk createValidDisk() {
      Set<String> tags = ImmutableSet.of("bosh:job=ccdb", "sys:sla=cesspool");

      List<QuotaLineItem> costs = new ArrayList<>();
      costs.add(new QuotaLineItem("disk", 1.0, QuotaUnit.COUNT));
      costs.add(new QuotaLineItem("storage.LOCAL_VMFS", 1.0, QuotaUnit.COUNT));

      PersistentDisk disk = new PersistentDisk();
      disk.setId("disk-id");
      disk.setSelfLink("http://localhost:9080/disks/disk-id");
      disk.setName("disk-name");
      disk.setState(DiskState.CREATING);
      disk.setCapacityGb(10);
      disk.setFlavor("disk-flavor");
      disk.setTags(tags);
      disk.setDatastore("datastore1");
      disk.setCost(costs);
      disk.setVms(ImmutableList.of("vm-id1", "vm-id2"));
      disk.setProjectId("project-id");
      return disk;
    }
  }
}
