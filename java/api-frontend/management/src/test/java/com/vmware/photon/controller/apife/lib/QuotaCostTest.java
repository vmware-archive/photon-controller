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

package com.vmware.photon.controller.apife.lib;

import com.vmware.photon.controller.apife.Data;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link QuotaCost}.
 */
public class QuotaCostTest {

  @Test
  public void actualCostTest() {
    QuotaCost cost = new QuotaCost(Data.vm100Cost);

    assertThat("(vm100): cost is vm*, vm.flavor, vm.cpu, vm.memory, and vm.cost",
        cost.getCostKeys().contains("vm"),
        is(true));
    assertThat("(vm100): cost is vm*, vm.flavor, vm.cpu, vm.memory, and vm.cost",
        cost.getCost("vm").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor*, vm.cpu, vm.memory, and vm.cost",
        cost.getCostKeys().contains("vm.flavor.core-100"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor*, vm.cpu, vm.memory, and vm.cost",
        cost.getCost("vm.flavor.core-100").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu*, vm.memory, and vm.cost",
        cost.getCostKeys().contains("vm.cpu"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu*, vm.memory, and vm.cost",
        cost.getCost("vm.cpu").getValue(),
        is(1.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory*, and vm.cost",
        cost.getCostKeys().contains("vm.memory"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory*, and vm.cost",
        cost.getCost("vm.memory").getValue(),
        is(2.0));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory, and vm.cost*",
        cost.getCostKeys().contains("vm.cost"),
        is(true));
    assertThat("(vm100): cost is vm, vm.flavor, vm.cpu, vm.memory, and vm.cost*",
        cost.getCost("vm.cost").getValue(),
        is(1.0));
    assertThat("(vm100): only 5 items are in the set",
        cost.getCostKeys().size(),
        is(5));
  }
}
