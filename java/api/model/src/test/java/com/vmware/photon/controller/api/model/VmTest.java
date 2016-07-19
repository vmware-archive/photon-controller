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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.Is.isA;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests {@link Vm}.
 */
public class VmTest {

  @Test
  public void testIsAnInfrastructure() throws Exception {
    MatcherAssert.assertThat(new Vm(), isA(Infrastructure.class));
  }

  private Vm createValidVm() {
    Set<String> tags = ImmutableSet.of("bosh:job=ccdb", "sys:sla=cesspool");

    Map<String, String> vmMetadata = new HashMap<>();
    vmMetadata.put("cpi", "{\"key\":\"value\"}");

    AttachedDisk disk1 = AttachedDisk.create(null, "bootdisk", EphemeralDisk.KIND, "core-100", 32, true);
    AttachedDisk disk2 = AttachedDisk.create(null, "datadisk0", PersistentDisk.KIND, "core-100", 100, false);
    AttachedDisk disk3 = AttachedDisk.create(null, "datadisk1", EphemeralDisk.KIND, "core-100", 400, false);

    Iso iso = new Iso();
    iso.setName("isoName");
    iso.setSize(100L);

    List<QuotaLineItem> costs = new ArrayList<>();
    costs.add(new QuotaLineItem("vm", 1.0, QuotaUnit.COUNT));
    costs.add(new QuotaLineItem("vm.flavor.core-100", 1.0, QuotaUnit.COUNT));
    costs.add(new QuotaLineItem("vm.cpu", 1.0, QuotaUnit.COUNT));
    costs.add(new QuotaLineItem("vm.memory", 2.0, QuotaUnit.GB));
    costs.add(new QuotaLineItem("vm.cost", 1.0, QuotaUnit.COUNT));

    // start with an API representation object and make sure that it serializes correctly to JSON, and that
    // the JSON correctly de-serializes the JSON into an object
    Vm vm = new Vm();
    vm.setId("1-vm-full");
    vm.setSelfLink("http://localhost:9080/v1/vms/1-vm-full");
    vm.setName("myvm");
    vm.setFlavor("core-100");
    vm.setTags(tags);
    vm.setHost("1.1.1.1");
    vm.setDatastore("datastore");
    vm.setState(VmState.CREATING);
    vm.setCost(costs);
    vm.setMetadata(vmMetadata);
    vm.setProjectId("project-id");
    vm.setAttachedDisks(ImmutableList.of(disk1, disk2, disk3));
    vm.addAttachedIso(iso);

    return vm;
  }

  /**
   * Tests VM serialization.
   */
  public class VmSerializationTest {

    private static final String JSON_FILE = "fixtures/vm.json";

    private Vm vm;

    @BeforeMethod
    public void setUp() {
      vm = createValidVm();
    }

    @Test
    public void testSerialization() throws Exception {
      MatcherAssert.assertThat(
          JsonHelpers.asJson(vm), sameJSONAs(JsonHelpers.jsonFixture(JSON_FILE)).allowingAnyArrayOrdering());
    }

    @Test
    public void testDeserialization() throws Exception {
      // clear fields that we ignore from serialization/deserialization
      vm.setProjectId(null);

      MatcherAssert.assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(JSON_FILE), Vm.class), is(vm));
    }
  }
}
