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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.helpers.JsonHelpers;

import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DiskCreateSpec}.
 */
public class DiskCreateSpecTest {

  @Test(enabled = false)
  private void dummy() {}

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    private DiskCreateSpec diskCreateSpec;

    @BeforeMethod
    public void setUp() throws Exception {
      List<LocalitySpec> affinities = new ArrayList<>();
      affinities.add(new LocalitySpec("vm-1", "vm"));
      affinities.add(new LocalitySpec("disk-1", "disk"));

      diskCreateSpec = new DiskCreateSpec();
      diskCreateSpec.setName("mydisk");
      diskCreateSpec.setKind(PersistentDisk.KIND);
      diskCreateSpec.setFlavor("good-flavor");
      diskCreateSpec.setCapacityGb(2);
      diskCreateSpec.setTags(ImmutableSet.of("bosh:job=ccdb", "sys:sla=cesspool"));
      diskCreateSpec.setAffinities(affinities);
    }

    @Test
    public void testSerialization() throws Exception {
      String json = JsonHelpers.jsonFixture("fixtures/disk-create-spec.json");

      assertThat(fromJson(json, DiskCreateSpec.class), is(diskCreateSpec));
      assertThat(asJson(diskCreateSpec), is(sameJSONAs(json)));
    }

    @Test
    public void testShortKind() throws Exception {

      String jsonIn = jsonFixture("fixtures/disk-create-spec-short-kind.json");
      String jsonOut = jsonFixture("fixtures/disk-create-spec.json");

      assertThat(fromJson(jsonIn, DiskCreateSpec.class), is(diskCreateSpec));
      assertThat(asJson(diskCreateSpec), is(sameJSONAs(jsonOut)));
    }

    @Test(expectedExceptions = JsonMappingException.class,
        expectedExceptionsMessageRegExp = ".*Unknown disk kind: invalid-kind.*")
    public void invalidKind() throws Exception {
      String json = jsonFixture("fixtures/disk-create-spec-invalid-kind.json");
      fromJson(json, DiskCreateSpec.class);
    }
  }
}
