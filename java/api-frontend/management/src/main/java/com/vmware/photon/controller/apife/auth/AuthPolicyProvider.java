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

package com.vmware.photon.controller.apife.auth;

import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.photon.controller.apife.auth.fetcher.Multiplexed;
import com.vmware.photon.controller.apife.auth.fetcher.SecurityGroupFetcher;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;
import com.vmware.photon.controller.apife.resources.routes.AvailableRoutes;

import com.google.inject.Inject;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class implementing the esxcloud authorization policy.
 */
public class AuthPolicyProvider implements PolicyProvider {
  /**
   * Group that all users with admin permissions are assigned to.
   */
  protected static final String DEFAULT_ADMIN_GROUP_NAME = "\\ESXCloudAdmins";

  /**
   * Logger instance.
   */
  private static final Logger logger = LoggerFactory.getLogger(PolicyProvider.class);

  /**
   * List of routes that do not require authentication.
   *
   * available is unauthenticated because it's intended for load balancers to detect if the system
   * is responding.
   */
  private static final String[] OPEN_ACCESS_ROUTES = {
      AuthRoutes.API.toLowerCase(),
      AvailableRoutes.API.toLowerCase(),
      "/api",
  };

  private final TransactionAuthorizationObjectResolver resolver;
  private final SecurityGroupFetcher fetcher;

  private final String defaultAdminGroup;

  @Inject
  public AuthPolicyProvider(TransactionAuthorizationObjectResolver resolver,
                            @Multiplexed SecurityGroupFetcher fetcher,
                            AuthConfig config) {
    this.resolver = resolver;
    this.fetcher = fetcher;
    this.defaultAdminGroup = config.getTenant() + DEFAULT_ADMIN_GROUP_NAME;
  }

  @Override
  public boolean isOpenAccessRoute(ContainerRequest request) {
    String path = getRequestPath(request);

    for (String route : OPEN_ACCESS_ROUTES) {
      if (path.startsWith(route)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check that the intersection of groups required for the request and the groups present in the token is not null.
   */
  @Override
  public void checkAccessPermissions(ContainerRequest request, ResourceServerAccessToken token)
      throws ExternalException {

    // Determine request authorization object.
    TransactionAuthorizationObject transactionAuthorizationObject = this.resolver.evaluate(request);

    // Determine security groups.
    Set<String> groups = this.fetcher.fetchSecurityGroups(transactionAuthorizationObject);
    if (groups.contains(SecurityGroupFetcher.EVERYONE)) {
      // everyone has access to this path
      return;
    }

    List<String> tokenGroups = token.getGroups().stream()
        .map(g -> g.replaceAll("^\"|\"$", ""))
        .collect(Collectors.toList());

    // Make a group copy, the collection is going to be changed.
    Set<String> intersectionGroup = new HashSet<>(groups);
    intersectionGroup.add(this.defaultAdminGroup);

    // Perform request and token group intersection, deny if empty.
    intersectionGroup.retainAll(tokenGroups);
    if (intersectionGroup.isEmpty()) {
      logger.info("Deny request: Token id {}, request groups {}", token.getJWTID(), groups);
      throw new ExternalException(ErrorCode.ACCESS_FORBIDDEN);
    }
  }

  private String getRequestPath(ContainerRequest request) {
    return "/" + request.getPath(true).toLowerCase();
  }
}
