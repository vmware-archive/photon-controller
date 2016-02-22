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
package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.RenamedFieldHandler;
import com.vmware.xenon.common.ServiceDocument;

import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * This class implements common upgrade utils and map.
 */
public class UpgradeUtils {
  public static final Map<String, String> SOURCE_DESTINATION_MAP = ImmutableMap.<String, String>builder()
      // uris for beta 1
      .put("/esxcloud/cloudstore/flavors", "/photon/cloudstore/flavors")
      .put("/esxcloud/cloudstore/images", "/photon/cloudstore/images")
      .put("/esxcloud/cloudstore/hosts", "/photon/cloudstore/hosts")
      .put("/esxcloud/cloudstore/networks", "/photon/cloudstore/networks")
      .put("/esxcloud/cloudstore/datastores", "/photon/cloudstore/datastores")
      .put("/provisioning/esxcloud/portgroups", "/photon/cloudstore/portgroups")
      .put("/esxcloud/cloudstore/tasks", "/photon/cloudstore/tasks")
      .put("/esxcloud/cloudstore/entity-locks", "/photon/cloudstore/entity-locks")
      .put("/esxcloud/cloudstore/projects", "/photon/cloudstore/projects")
      .put("/esxcloud/cloudstore/tenants", "/photon/cloudstore/tenants")
      .put("/esxcloud/cloudstore/resource-tickets", "/photon/cloudstore/resource-tickets")
      .put("/esxcloud/cloudstore/vms", "/photon/cloudstore/vms")
      .put("/esxcloud/cloudstore/disks", "/photon/cloudstore/disks")
      .put("/esxcloud/cloudstore/attached-disks", "/photon/cloudstore/attached-disks")
      .put("/esxcloud/cloudstore/tombstones", "/photon/cloudstore/tombstones")
      // uris after beta1
      .put("/photon/cloudstore/flavors", "/photon/cloudstore/flavors")
      .put("/photon/cloudstore/images", "/photon/cloudstore/images")
      .put("/photon/cloudstore/hosts", "/photon/cloudstore/hosts")
      .put("/photon/cloudstore/networks", "/photon/cloudstore/networks")
      .put("/photon/cloudstore/datastores", "/photon/cloudstore/datastores")
      .put("/photon/cloudstore/portgroups", "/photon/cloudstore/portgroups")
      .put("/photon/cloudstore/tasks", "/photon/cloudstore/tasks")
      .put("/photon/cloudstore/entity-locks", "/photon/cloudstore/entity-locks")
      .put("/photon/cloudstore/projects", "/photon/cloudstore/projects")
      .put("/photon/cloudstore/tenants", "/photon/cloudstore/tenants")
      .put("/photon/cloudstore/resource-tickets", "/photon/cloudstore/resource-tickets")
      .put("/photon/cloudstore/vms", "/photon/cloudstore/vms")
      .put("/photon/cloudstore/disks", "/photon/cloudstore/disks")
      .put("/photon/cloudstore/attached-disks", "/photon/cloudstore/attached-disks")
      .put("/photon/cloudstore/tombstones", "/photon/cloudstore/tombstones")
      .build();

    public static List<Field> handleRenamedField(Object source, ServiceDocument destination) {
        return RenamedFieldHandler.initialize(source, destination);
    }
}
