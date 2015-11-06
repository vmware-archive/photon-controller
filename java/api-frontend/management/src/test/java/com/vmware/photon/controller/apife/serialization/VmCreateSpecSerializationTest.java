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

import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;

import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.List;

/**
 * Tests {@link VmCreateSpec}.
 */
public class VmCreateSpecSerializationTest {

  private VmCreateSpec vmCreateSpec;

  @BeforeMethod
  public void setUp() throws Exception {
    List<LocalitySpec> affinities =
        ImmutableList.of(new LocalitySpec("disk-1", "disk"), new LocalitySpec("disk-2", "disk"));

    vmCreateSpec = new VmCreateSpec();
    vmCreateSpec.setName("myvm");
    vmCreateSpec.setFlavor("good-flavor");
    vmCreateSpec.setTags(ImmutableSet.of("bosh:job=ccdb", "sys:sla=cesspool"));
    vmCreateSpec.addDisk(
        new AttachedDiskCreateSpecBuilder().name("bootdisk").flavor("good-disk-100").bootDisk(true).build());
    vmCreateSpec.addDisk(
        new AttachedDiskCreateSpecBuilder().name("datadisk0").flavor("good-disk-100").capacityGb(100).build());
    vmCreateSpec.addDisk(
        new AttachedDiskCreateSpecBuilder().name("datadisk1").flavor("good-disk-100").capacityGb(400).build());
    vmCreateSpec.setEnvironment(ImmutableMap.of("k1", "v1", "k2", "v2"));
    vmCreateSpec.setAffinities(affinities);
    vmCreateSpec.setSourceImageId("image-id");
    vmCreateSpec.setNetworks(ImmutableList.of("network1", "network2"));
  }

  @Test
  public void testSerialization() throws Exception {
    String json = jsonFixture("fixtures/vmcreate.json");

    assertThat(fromJson(json, VmCreateSpec.class), is(vmCreateSpec));
    assertThat(asJson(vmCreateSpec), sameJSONAs(json).allowingAnyArrayOrdering());
  }

}
