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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Components of the system.
 */
public enum Component {
  PHOTON_CONTROLLER("photon-controller");
  private static final Map<String, Component> COMPONENT_MAP;
  static {
    ImmutableMap.Builder<String, Component> builder = ImmutableMap.builder();
    for (Component component : Component.values()) {
      builder.put(component.toString(), component);
    }
    COMPONENT_MAP = builder.build();
  }
  private final String moduleString;

  Component(String moduleString) {
    this.moduleString = moduleString;
  }

  /**
   * Translates a collection of Strings into Component Enums.
   */
  public static Set<Component> fromStrings(Collection<String> values) {

    List<Component> result = Lists.newArrayListWithExpectedSize(values.size());

    for (String value : values) {
      result.add(Component.fromString(value));
    }

    return EnumSet.copyOf(result);
  }

  public static Component fromString(String value) {
    Component result = COMPONENT_MAP.get(value);
    if (result == null) {
      throw new IllegalArgumentException(value + " is not a valid component!!");
    }
    return result;
  }

  @Override
  public String toString() {
    return moduleString;
  }
}
