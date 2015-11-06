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

package com.vmware.photon.controller.clustermanager.util;

import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility functions for cluster related logic.
 */
public class ClusterUtil {

  public static String createClusterTag(String clusterId) {
    return "cluster:" + clusterId;
  }

  public static String createClusterNodeTag(String clusterId, NodeType nodeType) {
    return createClusterTag(clusterId) + ":" + nodeType.toString();
  }

  public static Set<String> createClusterTags(String clusterId, NodeType nodeType) {
    Set<String> tags = new HashSet<>();
    tags.add(createClusterTag(clusterId));
    tags.add(createClusterNodeTag(clusterId, nodeType));
    return tags;
  }
}
