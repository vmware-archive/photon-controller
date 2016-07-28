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
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.model.Tenant;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of object that retrieves the security groups for a tenant authorization
 * object.
 */
public class TenantSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(TenantSecurityGroupFetcher.class);

  /**
   * Storage access object for tenant resources.
   */
  TenantBackend backend;

  @Inject
  public TenantSecurityGroupFetcher(TenantBackend backend) {
    this.backend = backend;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.TENANT,
        "authorizationObject must be of 'kind' TENANT.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.SELF,
        "authorizationObject must have 'strategy' SELF.");

    Set<String> securityGroups = new HashSet<>();
    try {
      Tenant tenant = backend.getApiRepresentation(authorizationObject.getId());
      securityGroups.addAll(tenant.getSecurityGroups().stream().map(g -> g.getName()).collect(Collectors.toList()));
    } catch (TenantNotFoundException ex) {
      logger.warn("invalid tenant id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing tenant {}", authorizationObject.getId());
    }

    return securityGroups;
  }
}
