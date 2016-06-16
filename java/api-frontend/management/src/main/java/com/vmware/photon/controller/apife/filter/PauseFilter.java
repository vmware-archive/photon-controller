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

package com.vmware.photon.controller.apife.filter;

import com.vmware.photon.controller.api.common.Responses;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.cloudstore.SystemConfig;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import com.sun.research.ws.wadl.HTTPMethods;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.uri.UriTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom RequestFilter used to pause the system.
 */
@Singleton
public class PauseFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(PauseFilter.class);
  private static final List<UriTemplate> CONSTRAINED_URIS = ImmutableList.of(
      new UriTemplate(VmResourceRoutes.VM_NETWORKS_PATH)
  );
  private static final List<UriTemplate> ALLOWABLE_URIS = ImmutableList.of(
      new UriTemplate(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.RESUME_SYSTEM_ACTION),
      new UriTemplate(DeploymentResourceRoutes.DEPLOYMENT_PATH + DeploymentResourceRoutes.PAUSE_BACKGROUND_TASKS_ACTION)
  );

  private SystemConfig systemConfig = null;

  public PauseFilter() {
    this.systemConfig = SystemConfig.getInstance();
  }

  public SystemConfig getSystemConfig() {
    return this.systemConfig;
  }

  /**
   * Filter to pause API calls.
   *
   * @param requestContext
   * @return
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    ContainerRequest request = (ContainerRequest) requestContext;
    String uri = getRequestPath(request);
    if (matchAnyUri(uri, ALLOWABLE_URIS)) {
      return;
    }

    if (HTTPMethods.GET.equals(HTTPMethods.fromValue(request.getMethod()))
        && !matchAnyUri(uri, CONSTRAINED_URIS)) {
      return;
    }

    boolean isPaused;
    try {
      isPaused = getSystemConfig().isPaused();
    } catch (Exception ex) {
      logger.error("serviceConfig.isPaused() throws error", ex);
      ExternalException e = new ExternalException(ErrorCode.INTERNAL_ERROR, ex.getMessage(), null);
      throw new WebApplicationException(ex, Responses.externalException(e));
    }

    if (isPaused) {
      logger.warn("System is paused");
      ExternalException e = new ExternalException(ErrorCode.SYSTEM_PAUSED, "System is paused", null);
      throw new WebApplicationException(e.getCause(), Responses.externalException(e));
    }
  }

  private boolean matchAnyUri(String uri, List<UriTemplate> uris) {
    for (UriTemplate constrainedUri : uris) {
      if (constrainedUri.match(uri, new ArrayList<>())) {
        return true;
      }
    }

    return false;
  }

  private String getRequestPath(ContainerRequest request) {
    return "/" + request.getPath(true).toLowerCase();
  }
}
