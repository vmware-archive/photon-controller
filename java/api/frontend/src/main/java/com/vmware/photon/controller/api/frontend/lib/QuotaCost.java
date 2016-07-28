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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.frontend.entities.QuotaLineItemEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The QuotaCost class is present in each API/Entity created within a project, and at a specific cost.
 * The cost structure tracks the cost as programmed by the cloud in full fidelity. Normally, quota is
 * enforced over a tiny subset of the cost spectrum. This object knows nothing about this and simply
 * represents the cost of an object.
 */
public class QuotaCost {

  private final Map<String, QuotaLineItemEntity> cost;

  public QuotaCost(List<QuotaLineItemEntity> items) {
    cost = new HashMap<>();
    for (QuotaLineItemEntity item : items) {
      cost.put(item.getKey(), item);
    }
  }

  public QuotaLineItemEntity getCost(String key) {
    return cost.get(key);
  }

  public Set<String> getCostKeys() {
    return cost.keySet();
  }

}
