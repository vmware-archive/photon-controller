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

package com.vmware.photon.controller.common.xenon.service;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.UpgradeInformation;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This service provides information about the services that will be upgraded.
 */
public class UpgradeInformationService extends StatelessService {

  public static final String SELF_LINK = ServiceUriPaths.UPGRADE_ROOT + "/upgrade-information";

  @Override
  public void handleGet(Operation getOperation) {
    EnumSet<ServiceOption> options = EnumSet.of(ServiceOption.FACTORY);
    Operation op = new Operation();
    op.setCompletion((o, e) -> {
      if (e != null) {
        getOperation.fail(e);
        return;
      }
      ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
      Set<String> factories = new HashSet<>(result.documentLinks);
      List<UpgradeInfo> info = MigrationUtils.findAllUpgradeServices().stream()
          .filter(i -> factories.contains(i.destinationFactoryServicePath))
          .map(i -> new UpgradeInfo(i))
          .collect(Collectors.toList());
      getOperation.setBody(Utils.toJson(false, true, new UpgradeList(info))).complete();
    });

    getHost().queryServiceUris(options, false, op);
  }

  /**
   * This class provides an workaround for deserializing generic lists.
   */
  public static class UpgradeList {
    public List<UpgradeInfo> list;

    public UpgradeList() {}

    public UpgradeList(List<UpgradeInfo> list) {
      this.list = list;
    }
  }

  /**
   * This class removes the type field of the {@link UpgradeInformation} class, because json cannot
   * serialize/de-serialize {@link Class} objects.
   */
  public static class UpgradeInfo {
    public String sourceFactoryServicePath;
    public String zookeeperServerSet;
    public String transformationServicePath;
    public String destinationFactoryServicePath;

    public UpgradeInfo() {}

    public UpgradeInfo(UpgradeInformation info) {
      this.sourceFactoryServicePath = info.sourceFactoryServicePath;
      this.zookeeperServerSet = info.zookeeperServerSet;
      this.transformationServicePath = info.transformationServicePath;
      this.destinationFactoryServicePath = info.destinationFactoryServicePath;
    }
  }
}
