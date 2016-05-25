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

import com.vmware.photon.controller.api.builders.DiskCreateSpecBuilder;
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test(dataProvider = "validDiskCreateSpecs")
    public void testValidDiskCreateSpecs(DiskCreateSpec spec) {
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validDiskCreateSpecs")
    private Object[][] getValidDiskCreateSpecs() {
      return new Object[][]{
          {new DiskCreateSpecBuilder().name("d").kind("persistent").flavor("f").capacityGb(2).build()},
          {new DiskCreateSpecBuilder().name("d").kind("persistent-disk").flavor("f").capacityGb(2).build()},
          {new DiskCreateSpecBuilder().name("d").kind("persistent").flavor("f").capacityGb(2).build()}
      };
    }

    @Test(dataProvider = "invalidDiskCreateSpecs")
    public void testInvalidDiskCreateSpecs(DiskCreateSpec spec, String errorMsg) {
      ImmutableList<String> violations = validator.validate(spec);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(errorMsg));
    }

    @DataProvider(name = "invalidDiskCreateSpecs")
    private Object[][] getInvalidDiskCreateSpecs() {
      String longName = new String();
      for (int i = 0; i < DiskCreateSpec.MAX_NAME_LENGTH + 1; i++) {
        longName += "d";
      }

      return new Object[][]{
          {new DiskCreateSpecBuilder().kind("persistent").flavor("f").capacityGb(2).build(),
              "name may not be null (was null)"},
          {new DiskCreateSpecBuilder().name("name!").kind("persistent").flavor("f").capacityGb(2).build(),
              "name : The specified disk name does not match pattern: ^[a-zA-Z][a-zA-Z0-9-]* (was name!)"},
          {new DiskCreateSpecBuilder().name(longName).kind("persistent").flavor("f").capacityGb(2).build(),
              "name size must be between 1 and 63 (was " + longName + ")"},

          {new DiskCreateSpecBuilder().name("d").kind("persistent").flavor("f").capacityGb(-2).build(),
              "capacityGb must be greater than or equal to 1 (was -2)"},
          {new DiskCreateSpecBuilder()
              .name("d").kind("persistent").flavor("f").capacityGb(DiskCreateSpec.MIN_CAPACITY_IN_GB - 1).build(),
              "capacityGb must be greater than or equal to 1 (was 0)"},

          {new DiskCreateSpecBuilder().name("d").kind("ephemeral").flavor("f").capacityGb(2).build(),
              "kind : The specified kind does not match : persistent-disk|persistent (was ephemeral-disk)"},
          {new DiskCreateSpecBuilder().name("d").kind("other-kind").flavor("f").capacityGb(2).build(),
              "kind : The specified kind does not match : persistent-disk|persistent (was other-kind)"}
      };
    }
  }

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

    @Test
    public void otherKind() throws Exception {
      diskCreateSpec.setKind("other-kind");

      String json = jsonFixture("fixtures/disk-create-spec-other-kind.json");
      assertThat(fromJson(json, DiskCreateSpec.class), is(diskCreateSpec));
      assertThat(asJson(diskCreateSpec), is(sameJSONAs(json)));
    }
  }
}
