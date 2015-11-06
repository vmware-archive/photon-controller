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

package com.vmware.photon.controller.apife.auth.fetcher;

import com.vmware.photon.controller.apife.auth.TransactionAuthorizationObject;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implements the default system level security group fetcher.
 */
@Singleton
public class MultiplexedSecurityGroupFetcher implements SecurityGroupFetcher {

  private Map<TransactionAuthorizationObject.Kind, SecurityGroupFetcher> fetcherMap;

  /**
   * Default constructor.
   *
   * @param deploymentFetcher
   * @param noneFetcher
   * @param tenantFetcher
   */
  @Inject
  public MultiplexedSecurityGroupFetcher(
      @None SecurityGroupFetcher noneFetcher,
      @Deployment SecurityGroupFetcher deploymentFetcher,
      @Tenant SecurityGroupFetcher tenantFetcher,
      @Project SecurityGroupFetcher projectFetcher,
      @ResourceTicket SecurityGroupFetcher resourceTicketFetcher,
      @Cluster SecurityGroupFetcher clusterFetcher,
      @Disk SecurityGroupFetcher diskFetcher,
      @Vm SecurityGroupFetcher vmFetcher) {
    this.fetcherMap = new HashMap<>();
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.NONE, noneFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.DEPLOYMENT, deploymentFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.TENANT, tenantFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.PROJECT, projectFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.RESOURCE_TICKET, resourceTicketFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.CLUSTER, clusterFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.DISK, diskFetcher);
    this.fetcherMap.put(TransactionAuthorizationObject.Kind.VM, vmFetcher);
  }

  /**
   * Constructor used for unit-testing.
   *
   * @param map
   */
  @VisibleForTesting
  protected MultiplexedSecurityGroupFetcher(Map<TransactionAuthorizationObject.Kind, SecurityGroupFetcher> map) {
    this.fetcherMap = map;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    SecurityGroupFetcher fetcher = this.fetcherMap.get(authorizationObject.getKind());
    if (null == fetcher) {
      throw new IllegalArgumentException(
          String.format("authorizationObject of 'kind' %s is not supported.", authorizationObject.getKind()));
    }

    return fetcher.fetchSecurityGroups(authorizationObject);
  }

  @VisibleForTesting
  protected Map<TransactionAuthorizationObject.Kind, SecurityGroupFetcher> getFetcherMap() {
    return this.fetcherMap;
  }
}
