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

package com.vmware.photon.controller.apife.serialization;

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.PersistentDisk;

import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Tests Disk serialization.
 */
public class DiskSerializationTest {

  @Test
  public void testSerialization() throws Exception {

    // start with an API representation object and make sure that it serializes correctly to JSON, and that
    // the JSON correctly de-serializes the JSON into an object
    PersistentDisk persistentDisk = new PersistentDisk();
    persistentDisk.setId("disk-id");
    persistentDisk.setSelfLink("http://localhost:9080/v1/disks/disk-id");
    persistentDisk.setName("disk-1");
    persistentDisk.setFlavor("core-100");
    persistentDisk.setState(DiskState.DETACHED);
    persistentDisk.setDatastore("datastore-1");
    persistentDisk.setCapacityGb(2);
    persistentDisk.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));

    assertThat(asJson(persistentDisk), sameJSONAs(jsonFixture("fixtures/disk.json")).allowingAnyArrayOrdering());
    assertThat(fromJson(jsonFixture("fixtures/disk.json"), PersistentDisk.class), is(persistentDisk));
  }
}
