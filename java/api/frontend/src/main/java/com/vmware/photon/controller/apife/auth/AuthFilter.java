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
import com.vmware.identity.openidconnect.client.TokenValidationException;
import com.vmware.photon.controller.apife.Responses;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.AvailableRoutes;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.auth.AuthOIDCClient;
import com.vmware.photon.controller.common.auth.AuthTokenHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;

/**
 * Custom RequestFilter used for Authentication and Authorization.
 * Reads Auth 2.0 access token from the request header.
 */
public class AuthFilter implements ContainerRequestFilter {

  // OAuth2.0 Authorization Request Header: http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-20#section-2.1
  public static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

  // OAuth2.0 Bearer Token: http://self-issued.info/docs/draft-ietf-oauth-v2-bearer.html
  public static final String AUTHORIZATION_METHOD = "Bearer ";

  private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

  private String authServerAddress;
  private int authServerPort;
  private String tenant;
  private String sharedSecret;
  private PolicyProvider policyProvider;
  private AuthTokenHandler tokenHandler;

  @Inject
  public AuthFilter(AuthConfig config, AuthPolicyProvider provider) {
    this.authServerAddress = config.getAuthServerAddress();
    this.authServerPort = config.getAuthServerPort();
    this.tenant = config.getTenant();
    this.sharedSecret = config.getSharedSecret();
    this.policyProvider = provider;
  }

  /**
   * Filter to authorize API calls.
   *
   * @param requestContext
   * @return
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    ContainerRequest request = (ContainerRequest) requestContext;
    try {
      // verify if access should be granted
      checkCallAuthorization(request);

      String requestPath = request.getPath(true);
      // The load balancer hits the /available every few seconds and
      // we don't need to spam the log, so we only log if it's
      // not /available
      if (!AvailableRoutes.API_PATH.equals(requestPath)) {
        logger.info("Allow: API call: {}", requestPath);
      }
    } catch (ExternalException e) {
      logger.warn("Deny: API call: {}", request.getPath(true), e);
      throw new WebApplicationException(e.getCause(), Responses.externalException(e));
    }
  }

  /**
   * Setter for the tokenHandler member.
   *
   * @param handler
   */
  @VisibleForTesting
  protected void setTokenHandler(AuthTokenHandler handler) {
    this.tokenHandler = handler;
  }

  /**
   * Setter for the policyProvider member.
   *
   * @param provider
   */
  @VisibleForTesting
  protected void setPolicyProvider(PolicyProvider provider) {
    this.policyProvider = provider;
  }

  /**
   * Checks Authorization for the incoming ContainerRequest.
   *
   * @param request
   */
  private void checkCallAuthorization(ContainerRequest request) throws ExternalException {
    if (this.policyProvider.isOpenAccessRoute(request)) {
      // we don't need to authenticate for this route
      return;
    }

    String jwtAccessToken = extractJwtAccessToken(request);

    if (jwtAccessToken.equals(this.sharedSecret)) {
      // we don't need to authenticate a service
      return;
    }

    ResourceServerAccessToken token = parseAccessToken(jwtAccessToken);
    this.policyProvider.checkAccessPermissions(request, token);
  }

  /**
   * Retrieve the authorization for the request.
   *
   * @param request
   * @return
   */
  private String extractJwtAccessToken(ContainerRequest request) throws ExternalException {
    // Read authorization.
    String bearerToken = request.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
    if (bearerToken == null) {
      throw new ExternalException(
          ErrorCode.MISSING_AUTH_TOKEN, "AuthToken was missing in the request", null);
    }

    // Only Bearer token is supported.
    if (!bearerToken.startsWith(AUTHORIZATION_METHOD)) {
      throw new ExternalException(
          ErrorCode.MALFORMED_AUTH_TOKEN, "Malformed AuthToken recevied", null);
    }

    return bearerToken.substring(AUTHORIZATION_METHOD.length());
  }

  /**
   * Check resource access rights.
   *
   * @param jwtAccessToken
   */
  private ResourceServerAccessToken parseAccessToken(String jwtAccessToken) throws ExternalException {
    this.initializeAuth();

    try {
      return this.tokenHandler.parseAccessToken(jwtAccessToken);
    } catch (TokenValidationException ex) {
      switch (ex.getTokenValidationError()) {
        case EXPIRED_TOKEN:
          throw new ExternalException(
              ErrorCode.EXPIRED_AUTH_TOKEN, "The OAuth token has expired", null);
        default:
          throw new ExternalException(
              ErrorCode.INVALID_AUTH_TOKEN, "Passed OAuth token is invalid.", null, ex);
      }
    }
  }

  /**
   * Lazily initializes the AuthTokenHandler object for the AuthFilter.
   *
   * @throws WebApplicationException
   */
  private void initializeAuth() throws ExternalException {
    if (this.tokenHandler == null) {
      synchronized (this) {
        if (this.tokenHandler == null) {
          try {
            this.tokenHandler = new AuthOIDCClient(authServerAddress, authServerPort, tenant).getTokenHandler();
          } catch (AuthException ex) {
            throw new ExternalException(
                ErrorCode.AUTH_INITIALIZATION_FAILURE, "AuthTokenHandler initialization failed", null, ex);
          }
        }
      }
    }
  }
}
