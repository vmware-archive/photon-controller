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

package com.vmware.photon.controller.api.frontend.auth;

import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.AuthRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.AvailableRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TenantResourceRoutes;

import com.google.common.annotations.VisibleForTesting;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Implements a policy service that implements an authorization scheme with just 2
 * roles: admin and regular user.
 */
public class BasicPolicyProvider implements PolicyProvider {

  /**
   * Group that all users with admin permissions are assigned to.
   */
  @VisibleForTesting
  protected static final String ADMIN_GROUP = "\\ESXCloudAdmins";

  /**
   * Logger instance.
   */
  private static final Logger logger = LoggerFactory.getLogger(BasicPolicyProvider.class);

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

  /**
   * List of rules to be used to restrict access to admin only users.
   */
  private static final PolicyRule[] ADMIN_RESTRICTIONS = {
      new PolicyRule(DeploymentResourceRoutes.API, null),
      new PolicyRule(HostResourceRoutes.API, null),
      new PolicyRule(ProjectResourceRoutes.API, Pattern.compile("delete", Pattern.CASE_INSENSITIVE)),
      new PolicyRule(TenantResourceRoutes.API, Pattern.compile("post|delete", Pattern.CASE_INSENSITIVE))
  };

  public BasicPolicyProvider() {
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

  @Override
  public void checkAccessPermissions(ContainerRequest request, ResourceServerAccessToken token)
      throws ExternalException {
    String requestPath = getRequestPath(request);

    if (isAdminOnlyRequest(requestPath, request.getMethod()) && !isAdminUser(token)) {
      logger.info("Token id {}, groups {}", token.getJWTID(), token.getGroups());
      throw new ExternalException(ErrorCode.ACCESS_FORBIDDEN);
    }
  }

  @VisibleForTesting
  protected PolicyRule[] getAdminRestrictions() {
    return ADMIN_RESTRICTIONS;
  }

  private String getRequestPath(ContainerRequest request) {
    return "/" + request.getPath(true).toLowerCase();
  }

  private boolean isAdminOnlyRequest(String path, String method) {
    for (PolicyRule rule : this.getAdminRestrictions()) {
      if (!path.startsWith(rule.routePrefix)) {
        continue;
      }

      if (rule.allowedMethods != null && !rule.allowedMethods.matcher(method).matches()) {
        continue;
      }

      return true;
    }

    return false;
  }

  private boolean isAdminUser(ResourceServerAccessToken token) {
    if (null == token.getGroups()) {
      return false;
    }

    for (String group : token.getGroups()) {
      if (group.endsWith(ADMIN_GROUP)) {
        return true;
      }
    }

    return false;
  }

  static class PolicyRule {
    public String routePrefix;
    public Pattern allowedMethods;

    public PolicyRule(String prefix, Pattern methods) {
      this.routePrefix = prefix;
      this.allowedMethods = methods;
    }
  }
}
