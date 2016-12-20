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

package com.vmware.photon.controller.api.frontend.filter;

import com.google.inject.Singleton;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/**
 * Custom RequestFilter used to redirect calls made to deprecated api starting with /network to /subnet.
 * This is to ensure backward compatibility to CLI and other clients that still use the old API.
 */
@Singleton
@PreMatching
public class NetworkToSubnetRedirectionFilter implements ContainerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(NetworkToSubnetRedirectionFilter.class);

  /**
   * Filter to detect API calls that contain /networks in them and redirect to /subnets instead.
   * <p>
   * Calls to /networks... get redirected to /subnets...
   * Calls to /projects/{id}/networks get redirected to /projects/{id}/subnets
   * Calls to /vms/{id}/networks get redirected to /vms/{id}/subnets
   *
   * @param requestContext
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    final UriInfo uriInfo = requestContext.getUriInfo();
    final URI oldRequestURI = uriInfo.getRequestUri();
    final String oldPath = oldRequestURI.getPath().toLowerCase();

    // String.startsWith should be more efficient than String.contains.
    // Using startsWith first should short circuit this filter quickly for most requests
    // that do not have "/networks" in them.

    if (oldPath.startsWith("/networks")) {
      redirectNetworkToSubnet(requestContext, uriInfo, oldRequestURI, oldPath);
      return;
    }

    if (oldPath.startsWith("/projects") || oldPath.startsWith("/vms")) {
      if (oldPath.contains("/networks")) {
        redirectNetworkToSubnet(requestContext, uriInfo, oldRequestURI, oldPath);
      }
    }
  }

  private void redirectNetworkToSubnet(ContainerRequestContext requestContext,
                                       UriInfo uriInfo,
                                       URI oldRequestURI,
                                       String oldPath) {
    String newPath = oldPath.replace("/networks", "/subnets");
    List<NameValuePair> params = URLEncodedUtils.parse(oldRequestURI, "UTF-8");

    UriBuilder uriBuilder = uriInfo.getBaseUriBuilder().path(newPath);
    for (NameValuePair pair : params) {
      uriBuilder.queryParam(pair.getName(), pair.getValue());
    }

    URI newRequestURI = uriBuilder.build();
    requestContext.setRequestUri(newRequestURI);
    logger.info("Redirecting {} to {}", oldRequestURI.toString(), newRequestURI.toString());
  }
}
