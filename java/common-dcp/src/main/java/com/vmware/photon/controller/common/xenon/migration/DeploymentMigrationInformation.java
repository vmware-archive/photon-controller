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
package com.vmware.photon.controller.common.xenon.migration;

import com.vmware.xenon.common.ServiceDocument;

import java.util.Objects;

/**
 * This class encapsulates information about a service that needs to be migrated during deployment.
 */
public class DeploymentMigrationInformation {

  public String factoryServicePath;
  public String zookeeperServerSetName;
  public Class<? extends ServiceDocument> serviceType;

  public DeploymentMigrationInformation() {}

  public DeploymentMigrationInformation(
      String factoryServicePath,
      String serviceName,
      Class<? extends ServiceDocument> serviceType) {
    this.zookeeperServerSetName = serviceName;
    this.factoryServicePath = factoryServicePath;
    this.serviceType = serviceType;
  }

  @Override
  public boolean equals(Object o) {
    if (o != null && o instanceof DeploymentMigrationInformation) {
      DeploymentMigrationInformation u = (DeploymentMigrationInformation) o;
      return Objects.equals(this.factoryServicePath, u.factoryServicePath)
          && Objects.equals(this.zookeeperServerSetName, u.zookeeperServerSetName)
          && Objects.equals(this.serviceType, u.serviceType);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.factoryServicePath,
        this.zookeeperServerSetName,
        this.serviceType);
  }
}
