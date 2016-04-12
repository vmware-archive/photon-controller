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

package com.vmware.photon.controller.common.xenon.upgrade;

import com.vmware.xenon.common.ServiceDocument;

import java.util.Objects;

/**
 * This class encapsulates information about a service that needs to be upgraded.
 */
public class UpgradeInformation {

  public String sourceFactoryServicePath;
  public String zookeeperServerSet;
  public String transformationServicePath;
  public String destinationFactoryServicePath;
  public Class<? extends ServiceDocument> serviceType;

  public UpgradeInformation() {}

  public UpgradeInformation(String sourceFactoryServicePath,
      String destinationFactoryServicePath,
      String serviceName,
      String transformationServicePath,
      Class<? extends ServiceDocument> serviceType) {
    this.zookeeperServerSet = serviceName;
    this.sourceFactoryServicePath = sourceFactoryServicePath;
    this.destinationFactoryServicePath = destinationFactoryServicePath;
    this.transformationServicePath = transformationServicePath;
    this.serviceType = serviceType;
  }

  @Override
  public boolean equals(Object o) {
    if (o != null && o instanceof UpgradeInformation) {
      UpgradeInformation u = (UpgradeInformation) o;
      return Objects.equals(this.sourceFactoryServicePath, u.sourceFactoryServicePath)
          && Objects.equals(this.destinationFactoryServicePath, u.destinationFactoryServicePath)
          && Objects.equals(this.zookeeperServerSet, u.zookeeperServerSet)
          && Objects.equals(this.transformationServicePath, u.transformationServicePath)
          && Objects.equals(this.serviceType, u.serviceType);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.sourceFactoryServicePath,
        this.destinationFactoryServicePath,
        this.zookeeperServerSet,
        this.transformationServicePath,
        this.serviceType);
  }
}
