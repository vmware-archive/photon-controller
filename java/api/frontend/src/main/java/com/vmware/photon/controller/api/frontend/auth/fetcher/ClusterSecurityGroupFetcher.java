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

package com.vmware.photon.controller.api.frontend.auth.fetcher;

import com.vmware.photon.controller.api.frontend.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.api.frontend.clients.ClusterFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.api.model.Cluster;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of object that retrieves the security groups for a cluster authorization
 * object.
 */
public class ClusterSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(ClusterSecurityGroupFetcher.class);

  /**
   * Storage access object for cluster resources.
   */
  ClusterFeClient clusterFeClient;

  /**
   * Fetcher used to retrieve the security groups for the parent project.
   */
  com.vmware.photon.controller.api.frontend.auth.fetcher.SecurityGroupFetcher projectFetcher;

  @Inject
  public ClusterSecurityGroupFetcher(ClusterFeClient clusterFeClient,
                                     @Project SecurityGroupFetcher projectFetcher) {
    this.clusterFeClient = clusterFeClient;
    this.projectFetcher = projectFetcher;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.CLUSTER,
        "authorizationObject must be of 'kind' CLUSTER.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.PARENT,
        "authorizationObject must have 'strategy' PARENT.");

    Set<String> securityGroups = new HashSet<>();
    try {
      Cluster cluster = clusterFeClient.get(authorizationObject.getId());
      securityGroups = projectFetcher.fetchSecurityGroups(
          new TransactionAuthorizationObject(
              TransactionAuthorizationObject.Kind.PROJECT,
              TransactionAuthorizationObject.Strategy.SELF,
              cluster.getProjectId()));

    } catch (ClusterNotFoundException ex) {
      logger.warn("invalid cluster id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing cluster {}", authorizationObject.getId());
    }

    return securityGroups;
  }
}
