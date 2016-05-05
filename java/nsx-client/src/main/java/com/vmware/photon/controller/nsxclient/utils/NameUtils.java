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

/**
 * Helper class to generate the names for NSX related objects.
 */
public class NameUtils {

  public static final String FABRIC_NODE_NAME_PREFIX = "PC-FabricNode-";
  public static final String FABRIC_NODE_DESCRIPTION_PREFIX = "Photon Controller Fabric Node ";
  public static final String TRANSPORT_NODE_NAME_PREFIX = "PC-TransportNode-";
  public static final String TRANSPORT_NODE_DESCRIPTION_PREFIX = "Photon Controller Transport Node ";
  public static final String TRANSPORT_ZONE_NAME_PREFIX = "PC-TransportZone-";
  public static final String TRANSPORT_ZONE_DESCRIPTION_PREFIX = "Photon Controller Transport Zone ";
  public static final String HOST_SWITCH_NAME = "PhotonControllerHostSwitch";
  public static final String LOGICAL_SWITCH_NAME_PREFIX = "PC-LogicalSwitch-";
  public static final String LOGICAL_ROUTER_NAME_PREFIX = "PC-LogicalRouter-";
  public static final String LOGICAL_ROUTER_DESCRIPTION_PREFIX = "Photon Controller Logical Router ";

  public static String getFabricNodeName(String id) {
    return FABRIC_NODE_NAME_PREFIX + id;
  }

  public static String getFabricNodeDescription(String id) {
    return FABRIC_NODE_DESCRIPTION_PREFIX + id;
  }

  public static String getTransportNodeName(String id) {
    return TRANSPORT_NODE_NAME_PREFIX + id;
  }

  public static String getTransportNodeDescription(String id) {
    return TRANSPORT_NODE_DESCRIPTION_PREFIX + id;
  }

  public static String getTransportZoneName(String id) {
    return TRANSPORT_ZONE_NAME_PREFIX + id;
  }

  public static String getTransportZoneDescription(String id) {
    return TRANSPORT_ZONE_DESCRIPTION_PREFIX + id;
  }

  public static String getLogicalSwitchName(String id) {
    return LOGICAL_SWITCH_NAME_PREFIX + id;
  }

  public static String getLogicalRouterName(String id) {
    return LOGICAL_ROUTER_NAME_PREFIX + id;
  }

  public static String getLogicalRouterDescription(String id) {
    return LOGICAL_ROUTER_DESCRIPTION_PREFIX + id;
  }
}
