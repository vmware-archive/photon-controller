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

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.UriInfo;

import java.net.URI;

/**
 * Custom RequestFilter used to redirect calls made to deprecated api starting with /network to /subnet.
 * This is to ensure backward compatibility to CLI and other clients that still use the old API.
 */
@Singleton
@PreMatching
public class NetworkToSubnetRedirectionFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(NetworkToSubnetRedirectionFilter.class);

  /**
   * Filter to pause API calls.
   *
   * @param requestContext
   * @return
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    final UriInfo uriInfo = requestContext.getUriInfo();
    final URI oldRequestURI = uriInfo.getRequestUri();
    final String oldPath = oldRequestURI.getPath().toLowerCase();
    if (oldPath.startsWith("/network")) {
      String newPath = oldPath.replace("/network", "/subnet");
      URI newRequestURI = uriInfo.getBaseUriBuilder().path(newPath).build();
      requestContext.setRequestUri(newRequestURI);
      logger.info("Redirecting {} to {}", oldRequestURI.toString(), newRequestURI.toString());
    }
  }
}
