/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.builders;

import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.LocalitySpec;

import java.util.List;
import java.util.Set;

/**
 * This class implements a builder for {@link DiskCreateSpec} object.
 */
public class DiskCreateSpecBuilder {
  private String name;

  private String kind;

  private String flavor;

  private Integer capacityGb;

  private Set<String> tags;

  private List<LocalitySpec> affinities;

  public DiskCreateSpecBuilder() {
  }

  public DiskCreateSpecBuilder name(String name) {
    this.name = name;
    return this;
  }

  public DiskCreateSpecBuilder kind(String kind) {
    this.kind = kind;
    return this;
  }

  public DiskCreateSpecBuilder flavor(String flavor) {
    this.flavor = flavor;
    return this;
  }

  public DiskCreateSpecBuilder capacityGb(Integer capacityGb) {
    this.capacityGb = capacityGb;
    return this;
  }

  public DiskCreateSpecBuilder tags(Set<String> tags) {
    this.tags = tags;
    return this;
  }

  public DiskCreateSpecBuilder affinities(List<LocalitySpec> affinities) {
    this.affinities = affinities;
    return this;
  }

  public DiskCreateSpec build() {
    DiskCreateSpec spec = new DiskCreateSpec();
    spec.setName(this.name);
    spec.setKind(this.kind);
    spec.setFlavor(this.flavor);
    spec.setCapacityGb(this.capacityGb);
    spec.setTags(this.tags);
    spec.setAffinities(this.affinities);

    return spec;
  }
}
