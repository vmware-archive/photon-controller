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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.entities.FlavorEntity;
import com.vmware.photon.controller.api.frontend.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-load flavor from files for test.
 */
@Singleton
public class FlavorLoader {

  private static final Logger logger = LoggerFactory.getLogger(FlavorLoader.class);

  private Map<String, Map<String, FlavorCreateSpec>> flavors;
  private List<FlavorCreateSpec> flavorList;

  public FlavorLoader(Map<String, Map<String, FlavorCreateSpec>> flavors) {
    this.flavors = flavors;

    ImmutableList.Builder<FlavorCreateSpec> builder = ImmutableList.builder();
    for (Map<String, FlavorCreateSpec> flavorMap : flavors.values()) {
      builder.addAll(flavorMap.values());
    }

    this.flavorList = builder.build();
  }

  /**
   * Returns cost for particular entity.
   *
   * @param flavorEntity
   * @return
   * @throws FlavorNotFoundException
   */
  public List<QuotaLineItemEntity> getCost(FlavorEntity flavorEntity) throws FlavorNotFoundException {
    return getCost(flavorEntity.getKind(), flavorEntity.getName());
  }

  /**
   * Returns cost for particular kind and flavor.
   *
   * @param kind       entity kind (vm, disk etc.)
   * @param flavorName flavor name
   * @return Cost of a given flavor
   */
  public List<QuotaLineItemEntity> getCost(String kind, String flavorName) throws FlavorNotFoundException {
    checkNotNull(kind);
    checkNotNull(flavorName);

    Map<String, FlavorCreateSpec> flavorMap = flavors.get(kind);

    if (flavorMap == null) {
      throw new FlavorNotFoundException(kind, flavorName);
    }

    FlavorCreateSpec flavor = flavorMap.get(flavorName);

    if (flavor == null) {
      throw new FlavorNotFoundException(kind, flavorName);
    }

    List<QuotaLineItemEntity> result = new ArrayList<>();
    for (QuotaLineItem item : flavor.getCost()) {
      result.add(new QuotaLineItemEntity(item.getKey(), item.getValue(), item.getUnit()));
    }
    return result;
  }

  /**
   * Get all pre-loaded flavors.
   */
  public List<FlavorCreateSpec> getAllFlavors() {
    return flavorList;
  }
}
