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
package com.vmware.photon.controller.deployer.xenon.upgrade;

import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.UpgradeInformation;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * The ReflectionTransformationService will transform a service by reflecting on all the fields
 * and renaming any that have the {@link RenameField} annotation that indicates it should be renamed.
 */
public class ReflectionTransformationService extends StatelessService {

  public static final String SELF_LINK = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK;

  @Override
  public void handlePost(Operation postOperation) {
    Map<?, ?> documents = postOperation.getBody(Map.class);
    Map<String, String> results = new HashMap<>();

    if (!documents.isEmpty()) {
      Class<? extends ServiceDocument> documentType = findDocumentType(documents);
      if (documentType == null) {
        postOperation.fail(new Exception("Could not find a factory matching provided path"));
        return;
      }

      for (Map.Entry<?, ?> entry : documents.entrySet()) {
        String factoryPath = Utils.fromJson(entry.getValue(), String.class);

        ServiceDocument convertedServiceDocument = Utils.fromJson(entry.getKey(), documentType);
        MigrationUtils.handleRenamedField(entry.getKey(), convertedServiceDocument);

        results.put(Utils.toJson(convertedServiceDocument), factoryPath);
      }
    }

    postOperation.setBody(results).complete();
  }

  private Class<? extends ServiceDocument> findDocumentType(Map<?, ?> documents) {
    String factoryPath = Utils.fromJson(documents.values().iterator().next(), String.class);
    for (UpgradeInformation info : HostUtils.getDeployerContext(this).getUpgradeInformation()) {
      if (info.destinationFactoryServicePath.equals(factoryPath)) {
        return info.serviceType;
      }
    }
    return null;
  }
}
