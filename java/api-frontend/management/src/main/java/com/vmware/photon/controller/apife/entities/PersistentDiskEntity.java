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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.PersistentDisk;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent disk entity.
 */
public class PersistentDiskEntity extends BaseDiskEntity {

  private List<LocalityEntity> affinities = new ArrayList<>();

  private String agent;

  @Override
  public String getKind() {
    return PersistentDisk.KIND;
  }

  public List<LocalityEntity> getAffinities() {
    return affinities;
  }

  public void setAffinities(List<LocalityEntity> affinities) {
    this.affinities = affinities;
  }

  public List<String> getAffinities(String kind) {
    List<String> results = new ArrayList<>();
    if (affinities == null) {
      return results;
    }

    for (LocalityEntity affinity : affinities) {
      if (java.util.Objects.equals(affinity.getKind(), kind)) {
        results.add(affinity.getResourceId());
      }
    }

    return results;
  }

  public String getAffinity(String kind) {
    if (affinities == null) {
      return null;
    }

    for (LocalityEntity affinity : affinities) {
      if (java.util.Objects.equals(affinity.getKind(), kind)) {
        return affinity.getResourceId();
      }
    }

    return null;
  }

  @Override
  public String getAgent() {
    return agent;
  }

  @Override
  public void setAgent(String agent) {
    this.agent = agent;
  }
}
