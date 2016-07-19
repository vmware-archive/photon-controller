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

import com.vmware.photon.controller.api.model.base.FlavoredCompact;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Tests {@link AttachedDisk}.
 */
public class AttachedDiskTest {

  private Validator validator = new Validator();
  private AttachedDisk disk = AttachedDisk.create("id", "name", "kind", "flavor", 1024, true);

  @Test
  public void testIsAFlavoredCompact() throws Exception {
    MatcherAssert.assertThat(disk, isA(FlavoredCompact.class));
  }

  @Test
  public void testNegativeCapacity() throws Exception {
    AttachedDisk disk = AttachedDisk.create("id", "name", "kind", "flavor", -10, true);
    ImmutableList<String> violations = validator.validate(disk);

    assertThat(violations.size(), is(1));
    assertThat(violations.get(0), containsString("must be greater than or equal to 1"));
  }

  @Test
  public void testOneGbCapacity() throws Exception {
    AttachedDisk disk = AttachedDisk.create("id", "name", "kind", "flavor", 1, true);
    ImmutableList<String> violations = validator.validate(disk);

    assertThat(violations.size(), is(0));
  }


  @Test
  public void testSerializesToJson() throws Exception {
    MatcherAssert.assertThat(
        JsonHelpers.asJson(disk), is(equalTo(JsonHelpers.jsonFixture("fixtures/attached-disk.json"))));
  }

  @Test
  public void testDeserializesFromJson() throws Exception {
    MatcherAssert.assertThat(
        JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/attached-disk.json"), AttachedDisk.class), is(disk));
  }
}
