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
import com.vmware.photon.controller.api.frontend.backends.ResourceTicketBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.api.model.ResourceTicket;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of object that retrieves the security groups for a resource-ticket authorization
 * object.
 */
public class ResourceTicketSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(ResourceTicketSecurityGroupFetcher.class);

  /**
   * Storage access object for resource-ticket resources.
   */
  ResourceTicketBackend backend;

  /**
   * Fetcher used to retrieve the security groups for the parent tenant.
   */
  SecurityGroupFetcher tenantFetcher;

  @Inject
  public ResourceTicketSecurityGroupFetcher(ResourceTicketBackend backend,
                                            @Tenant SecurityGroupFetcher tenantFetcher) {
    this.backend = backend;
    this.tenantFetcher = tenantFetcher;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.RESOURCE_TICKET,
        "authorizationObject must be of 'kind' RESOURCE_TICKET.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.PARENT,
        "authorizationObject must have 'strategy' PARENT.");

    Set<String> securityGroups = new HashSet<>();
    try {
      ResourceTicket ticket = backend.getApiRepresentation(authorizationObject.getId());
      securityGroups = tenantFetcher.fetchSecurityGroups(
          new TransactionAuthorizationObject(
              TransactionAuthorizationObject.Kind.TENANT,
              TransactionAuthorizationObject.Strategy.SELF,
              ticket.getTenantId()));

    } catch (ResourceTicketNotFoundException ex) {
      logger.warn("invalid resource-ticket id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing resource-ticket {}", authorizationObject.getId());
    }

    return securityGroups;
  }
}
