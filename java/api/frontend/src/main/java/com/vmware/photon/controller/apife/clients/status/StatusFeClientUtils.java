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

package com.vmware.photon.controller.apife.clients.status;

import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper methods to support the StatusFeClient class.
 */
public class StatusFeClientUtils {

  public static final Map<StatusType, Integer> STATUS_MAP = ImmutableMap.of(
      StatusType.READY, 0,
      StatusType.INITIALIZING, 1,
      StatusType.PARTIAL_ERROR, 2,
      StatusType.UNREACHABLE, 3,
      StatusType.ERROR, 4);

  /**
   * This method is used by StatusFeClient to compute status of an component using status of all the instances.
   *
   * @param componentStatus
   * @return
   */
  public static void computeSingleComponentStatus(ComponentStatus componentStatus) {
    Map<StatusType, Long> stats = componentStatus.getInstances().stream()
        .filter(instance -> instance.getStatus() != null)
        .collect(Collectors.groupingBy(instance -> instance.getStatus(), Collectors.counting()));
    componentStatus.setStats(stats.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> Long.toString(e.getValue()))));

    componentStatus.setStatus(getSingleComponentStatus(stats.keySet()));
  }

  /**
   * Return StatusType for whole Component based on its instances statuses.
   *
   * @param statusTypes
   * @return
   */
  private static StatusType getSingleComponentStatus(Set<StatusType> statusTypes) {
    if (statusTypes.isEmpty()) {
      return StatusType.UNREACHABLE;
    }

    if (containAnyStatus(statusTypes, StatusType.ERROR, StatusType.UNREACHABLE) &&
        containAnyStatus(statusTypes, StatusType.INITIALIZING, StatusType.READY)) {
      return StatusType.PARTIAL_ERROR;
    }

    return statusTypes.stream()
        .max(Comparator.comparing(statusType -> STATUS_MAP.get(statusType))).get();
  }

  private static boolean containAnyStatus(Set<StatusType> statusTypes, StatusType... statuses) {
    for (StatusType status : statuses) {
      if (statusTypes.contains(status)) {
        return true;
      }
    }
    return false;
  }

}
