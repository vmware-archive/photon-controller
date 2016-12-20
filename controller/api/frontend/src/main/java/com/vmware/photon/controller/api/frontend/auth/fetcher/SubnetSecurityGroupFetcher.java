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
import com.vmware.photon.controller.api.frontend.clients.VirtualNetworkFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.api.model.VirtualSubnet;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of object that retrieves the security groups for a Subnet authorization
 * object.
 */
public class SubnetSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(SubnetSecurityGroupFetcher.class);

  /**
   * Storage access object for Subnet resources.
   */
  VirtualNetworkFeClient virtualNetworkFeClient;

  /**
   * Fetcher used to retrieve the security groups for the parent project.
   */
  SecurityGroupFetcher projectFetcher;

  @Inject
  public SubnetSecurityGroupFetcher(VirtualNetworkFeClient virtualNetworkFeClient,
                                    @Project SecurityGroupFetcher projectFetcher) {
    this.virtualNetworkFeClient = virtualNetworkFeClient;
    this.projectFetcher = projectFetcher;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.SUBNET,
        "authorizationObject must be of 'kind' SUBNET.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.PARENT,
        "authorizationObject must have 'strategy' PARENT.");

    Set<String> securityGroups = new HashSet<>();
    try {
      VirtualSubnet subnet = virtualNetworkFeClient.get(authorizationObject.getId());
      securityGroups = projectFetcher.fetchSecurityGroups(
          new TransactionAuthorizationObject(
              TransactionAuthorizationObject.Kind.PROJECT,
              TransactionAuthorizationObject.Strategy.SELF,
              subnet.getParentId()));

    } catch (NetworkNotFoundException ex) {
      logger.warn("invalid virtual subnet id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing virtual network {}", authorizationObject.getId());
    }

    return securityGroups;
  }
}
