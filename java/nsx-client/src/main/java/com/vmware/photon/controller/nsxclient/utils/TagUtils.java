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

package com.vmware.photon.controller.nsxclient.utils;

import com.vmware.photon.controller.nsxclient.models.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class related to tagging NSX objects.
 */
public class TagUtils {
  public static final String VIRTUAL_NETWORK_TAG_SCOPE = "virtualNetwork";

  public static List<Tag> getLogicalSwitchTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  public static List<Tag> getLogicalRouterTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  public static List<Tag> getLogicalSwitchUplinkPortTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  public static List<Tag> getLogicalRouterUplinkPortTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  public static List<Tag> getLogicalRouterDownlinkPortTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  public static List<Tag> getTier0RouterDownlinkPortTags(String virtualNetworkId) {
    List<Tag> tags = new ArrayList<>();
    tags.add(getVirtualNetworkTag(virtualNetworkId));

    return tags;
  }

  private static Tag getVirtualNetworkTag(String virtualNetworkId) {
    Tag tag = new Tag();
    tag.setScope(VIRTUAL_NETWORK_TAG_SCOPE);
    tag.setTag(virtualNetworkId);

    return tag;
  }
}
