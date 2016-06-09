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
package com.vmware.photon.controller.cloudstore.xenon.upgrade;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * This service describe the transformation applied to the {@link HostService.State} documents during
 * upgrade.
 */
public class HostTransformationService extends StatelessService {

  public static final String SELF_LINK = ServiceUriPaths.UPGRADE_ROOT + "/host-transformation";

  @Override
  public void handlePost(Operation postOperation) {
    try {
      Map<?, ?> documents = postOperation.getBody(Map.class);
      Map<String, String> results = new HashMap<>();

      if (!documents.isEmpty()) {
        for (Map.Entry<?, ?> entry : documents.entrySet()) {
          String factoryPath = Utils.fromJson(entry.getValue(), String.class);

          // perform field renames
          HostService.State convertedServiceDocument = Utils.fromJson(entry.getKey(), HostService.State.class);
          MigrationUtils.handleRenamedField(entry.getKey(), convertedServiceDocument);

          // change usage tags to be cloud only, this will allow us to use the old management hosts
          // for future place requests.
          convertedServiceDocument.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));

          results.put(Utils.toJson(false, false, convertedServiceDocument), factoryPath);
        }
      }

      postOperation.setBody(results).complete();
    } catch (Throwable e) {
      postOperation.fail(e);
    }
  }
}
