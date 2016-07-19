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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.apife.backends.FlavorLoader;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collection of sample data for tags, flavors etc.
 */
public class Data {

  public static final List<String> BAD_TAGS = ImmutableList.of("sys", "sys:foo:bar=bar", "sys_vm:role=", "=foo",
      "sys:=foo", ":bar=foo", "sys:foo=1foo", "sys:***bar=baz", " foo:bar=baz", "foo:bar =bar", "1foo:bar=bar",
      "foo:bar%=baz", "foo%3Abaz%3Dbar", "!@#$%^&*øsys:iâfoo=bar", "foo:bar=x=y");
  public static final List<String> GOOD_TAGS = ImmutableList.of("sys:foo", "sys:foo=bar", "sys_vm:role=frontend");
  /**
   * This set of tags push the length limits of each tag component: namespace, predicate, and value.
   * The length limit is less than or equal to 63. In this class we have tags where each component
   * is 62 or 63 long. Or just inside the limit, so all these long tags are good.
   */
  public static final List<String> GOOD_LONG_TAGS = ImmutableList.of(
      // 1, 2
      // 62, 63 char namespace, predicate, value
      "X:foo=bar",
      "XX:foo=bar",
      "XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:foo=bar",
      "XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:foo=bar",
      "foo:X=bar",
      "foo:XX=bar",
      "foo:XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=bar",
      "foo:XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=bar",
      "foo:foo=XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",
      "foo:foo=XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",
      "foo:foo=X",
      "foo:foo=XX",

      // 62/63 all fields
      "X:X=X",
      "XX:XX=XX",
      "XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:" +
          "XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=" +
          "XXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",
      "XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:" +
          "XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=" +
          "XXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb"
  );
  /**
   * See (@Link GOOD_LONG_TAGS}. In this class we have tags where each component
   * is 64 or 65 long. Or just over the the limit, so all these long tags should fail.
   */
  public static final List<String> BAD_LONG_TAGS = ImmutableList.of(
      // 0 on either side
      // 64, 65 char namespace, predicate, value
      ":foo=bar",
      "X:=bar",
      "X:Y=",
      "XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:foo=bar",
      "XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:foo=bar",
      "foo:XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=bar",
      "foo:XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=bar",
      "foo:foo=XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",
      "foo:foo=XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",

      // 64/65 all fields
      "XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:" +
          "XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=" +
          "XXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb",
      "XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb:" +
          "XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb=" +
          "XXXXXaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbbaaaaabbbbb"
  );
  public static List<QuotaLineItemEntity> vm100Cost;
  public static List<QuotaLineItem> vm100CostApiForm;
  public static List<QuotaLineItemEntity> vm110Cost;
  public static List<QuotaLineItemEntity> vm200Cost;
  public static List<QuotaLineItemEntity> vm220Cost;
  public static List<QuotaLineItemEntity> vm240Cost;
  public static List<QuotaLineItemEntity> vm245Cost;
  public static List<QuotaLineItemEntity> vm280Cost;
  public static List<QuotaLineItemEntity> vm285Cost;
  public static List<QuotaLineItemEntity> large10000Limits;
  public static Set<String> large10000KeySet;
  public static List<QuotaLineItemEntity> small100Limits;
  public static Set<String> small100KeySet;
  public static List<QuotaLineItemEntity> small50Limits;
  public static Set<String> small50KeySet;
  public static List<QuotaLineItemEntity> small50WithExtraLimits;
  public static Set<String> small50WithExtraLimitsKeySet;
  public static List<QuotaLineItemEntity> small50WithMissingLimits;
  public static Set<String> small50WithMissingLimitsKeySet;
  public static List<QuotaLineItemEntity> baseLimits;
  public static Set<String> baseKeySet;
  public static List<QuotaLineItemEntity> baseLimitsDecreaser;
  public static List<QuotaLineItemEntity> partialBaseLimits;
  public static List<QuotaLineItemEntity> partialBaseLimitsDecreaser;
  public static List<QuotaLineItemEntity> partialMismatchBaseLimitsDecreaser;
  public static List<QuotaLineItemEntity> mismatchBaseLimits;
  public static List<QuotaLineItemEntity> oneVmCostLimit;
  public static List<QuotaLineItemEntity> vmTestingLimit;
  public static List<QuotaLineItemEntity> memoryLimits;
  public static List<QuotaLineItemEntity> subdivide10Limits;
  public static List<QuotaLineItemEntity> noMatchLimits;

  private Data() {
  }

  private static List<QuotaLineItem> transformListBtoA(
      List<QuotaLineItemEntity> list) {

    List<QuotaLineItem> response = new ArrayList<>();
    for (QuotaLineItemEntity qlie : list) {
      QuotaLineItem qli = new QuotaLineItem();
      qli.setKey(qlie.getKey());
      qli.setValue(qlie.getValue());
      qli.setUnit(qlie.getUnit());
      response.add(qli);
    }
    return response;
  }

  static {
    // HACK: this is a hack to support current Data test layout
    // TODO(olegs): remove after this class is refactored
    try {
      FlavorLoader flavorLoader = new TestModule().getFlavorLoader();
      Data.vm100Cost = flavorLoader.getCost("vm", "core-100");
      Data.vm100CostApiForm = Data.transformListBtoA(flavorLoader.getCost("vm", "core-100"));
      Data.vm110Cost = flavorLoader.getCost("vm", "core-110");
      Data.vm200Cost = flavorLoader.getCost("vm", "core-200");
      Data.vm220Cost = flavorLoader.getCost("vm", "core-220");
      Data.vm240Cost = flavorLoader.getCost("vm", "core-240");
      Data.vm245Cost = flavorLoader.getCost("vm", "core-245");
      Data.vm280Cost = flavorLoader.getCost("vm", "core-280");
      Data.vm285Cost = flavorLoader.getCost("vm", "core-285");
    } catch (IOException | FlavorNotFoundException e) {
      throw new RuntimeException(e);
    }

    // large and small tenant level resource ticket limits
    large10000Limits = new ArrayList<>();
    large10000Limits.add(new QuotaLineItemEntity("vm.cost", 10000.0, QuotaUnit.COUNT));
    large10000Limits.add(new QuotaLineItemEntity("persistent-disk.cost", 1000.0, QuotaUnit.COUNT));
    large10000Limits.add(new QuotaLineItemEntity("network.cost", 1000.0, QuotaUnit.COUNT));
    large10000Limits.add(new QuotaLineItemEntity("ephemeral-disk.cost", 10000.0, QuotaUnit.COUNT));
    large10000KeySet = ImmutableSet.of("vm.cost", "persistent-disk.cost", "network.cost", "ephemeral-disk.cost");

    small100Limits = new ArrayList<>();
    small100Limits.add(new QuotaLineItemEntity("vm.cost", 100.0, QuotaUnit.COUNT));
    small100Limits.add(new QuotaLineItemEntity("persistent-disk.cost", 100.0, QuotaUnit.COUNT));
    small100Limits.add(new QuotaLineItemEntity("network.cost", 100.0, QuotaUnit.COUNT));
    small100Limits.add(new QuotaLineItemEntity("ephemeral-disk.cost", 100.0, QuotaUnit.COUNT));
    small100KeySet = ImmutableSet.of("vm.cost", "persistent-disk.cost", "network.cost", "ephemeral-disk.cost");

    small50WithExtraLimits = new ArrayList<>();
    small50WithExtraLimits.add(new QuotaLineItemEntity("vm.cost", 50.0, QuotaUnit.COUNT));
    small50WithExtraLimits.add(new QuotaLineItemEntity("vm.flavor.core-200", 0.0, QuotaUnit.COUNT));
    small50WithExtraLimits.add(new QuotaLineItemEntity("persistent-disk.cost", 50.0, QuotaUnit.COUNT));
    small50WithExtraLimits.add(new QuotaLineItemEntity("network.cost", 50.0, QuotaUnit.COUNT));
    small50WithExtraLimits.add(new QuotaLineItemEntity("ephemeral-disk.cost", 50.0, QuotaUnit.COUNT));
    small50WithExtraLimitsKeySet = ImmutableSet.of("vm.cost", "vm.flavor.core-200", "persistent-disk.cost",
        "network.cost", "ephemeral-disk.cost");

    small50WithMissingLimits = new ArrayList<>();
    small50WithMissingLimits.add(new QuotaLineItemEntity("vm.cost", 50.0, QuotaUnit.COUNT));
    small50WithMissingLimits.add(new QuotaLineItemEntity("ephemeral-disk.cost", 50.0, QuotaUnit.COUNT));
    small50WithMissingLimitsKeySet = ImmutableSet.of("vm.cost", "ephemeral-disk.cost");

    small50Limits = new ArrayList<>();
    small50Limits.add(new QuotaLineItemEntity("vm.cost", 50.0, QuotaUnit.COUNT));
    small50Limits.add(new QuotaLineItemEntity("persistent-disk.cost", 50.0, QuotaUnit.COUNT));
    small50Limits.add(new QuotaLineItemEntity("network.cost", 50.0, QuotaUnit.COUNT));
    small50Limits.add(new QuotaLineItemEntity("ephemeral-disk.cost", 50.0, QuotaUnit.COUNT));
    small50KeySet = ImmutableSet.of("vm.cost", "persistent-disk.cost", "network.cost", "ephemeral-disk.cost");

    // 10vm's or 5.0 cost units of VM
    baseLimits = new ArrayList<>();
    baseLimits.add(new QuotaLineItemEntity("vm", 10.0, QuotaUnit.COUNT));
    baseLimits.add(new QuotaLineItemEntity("vm.testing", 10.0, QuotaUnit.COUNT));
    baseLimits.add(new QuotaLineItemEntity("vm.cost", 5.0, QuotaUnit.COUNT));
    baseKeySet = ImmutableSet.of("vm", "vm.testing", "vm.cost");

    baseLimitsDecreaser = new ArrayList<>();
    baseLimitsDecreaser.add(new QuotaLineItemEntity("vm", 1.0, QuotaUnit.COUNT));
    baseLimitsDecreaser.add(new QuotaLineItemEntity("vm.testing", 1.0, QuotaUnit.COUNT));
    baseLimitsDecreaser.add(new QuotaLineItemEntity("vm.cost", 1.0, QuotaUnit.COUNT));

    partialBaseLimits = new ArrayList<>();
    partialBaseLimits.add(new QuotaLineItemEntity("vm", 10.0, QuotaUnit.COUNT));

    partialBaseLimitsDecreaser = new ArrayList<>();
    partialBaseLimitsDecreaser.add(new QuotaLineItemEntity("vm", 1.0, QuotaUnit.COUNT));
    partialBaseLimitsDecreaser.add(new QuotaLineItemEntity("vm.testing", 1.0, QuotaUnit.COUNT));

    partialMismatchBaseLimitsDecreaser = new ArrayList<>();
    partialMismatchBaseLimitsDecreaser.add(new QuotaLineItemEntity("vm", 1.0, QuotaUnit.COUNT));
    partialMismatchBaseLimitsDecreaser.add(new QuotaLineItemEntity("vm.foobar", 1.0, QuotaUnit.COUNT));

    mismatchBaseLimits = new ArrayList<>();
    mismatchBaseLimits.add(new QuotaLineItemEntity("persistent-disk", 10.0, QuotaUnit.COUNT));

    oneVmCostLimit = new ArrayList<>();
    oneVmCostLimit.add(new QuotaLineItemEntity("vm.cost", 1.0, QuotaUnit.COUNT));

    vmTestingLimit = new ArrayList<>();
    vmTestingLimit.add(new QuotaLineItemEntity("vm.testing", 5.0, QuotaUnit.COUNT));

    // 256 GB of memory
    memoryLimits = new ArrayList<>();
    memoryLimits.add(new QuotaLineItemEntity("vm.memory", 256, QuotaUnit.GB));

    // subdivide 10% limits
    subdivide10Limits = new ArrayList<>();
    subdivide10Limits.add(new QuotaLineItemEntity("subdivide.percent", 10.0, QuotaUnit.COUNT));

    // doesn't match anything
    noMatchLimits = new ArrayList<>();
    noMatchLimits.add(new QuotaLineItemEntity("nokind.capacity", 756, QuotaUnit.MB));
    noMatchLimits.add(new QuotaLineItemEntity("nokind", 1, QuotaUnit.COUNT));
    noMatchLimits.add(new QuotaLineItemEntity("nokind.cost", 1, QuotaUnit.COUNT));
  }
}
