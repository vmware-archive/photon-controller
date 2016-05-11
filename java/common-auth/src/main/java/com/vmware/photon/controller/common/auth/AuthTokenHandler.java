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

package com.vmware.photon.controller.common.auth;

import com.vmware.identity.openidconnect.client.GroupMembershipType;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCServerException;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.SSLConnectionException;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.client.TokenValidationException;
import com.vmware.identity.openidconnect.common.PasswordGrant;
import com.vmware.identity.openidconnect.common.RefreshToken;
import com.vmware.identity.openidconnect.common.RefreshTokenGrant;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Authorization token retrieval logic.
 */
public class AuthTokenHandler {

  private final OIDCClient oidcClient;
  private final RSAPublicKey providerPublicKey;

  /**
   * Constructor.
   */
  AuthTokenHandler(OIDCClient oidcClient, RSAPublicKey providerPublickKey) {
    this.oidcClient = oidcClient;
    this.providerPublicKey = providerPublickKey;
  }

  /**
   * Get id, access and refresh tokens by username and password.
   *
   * @return AccessToken.
   */
  public OIDCTokens getAuthTokensByPassword(String username, String password) throws AuthException {
    return getAuthTokensByPassword(username, password, AuthOIDCClient.ResourceServer.rs_esxcloud);
  }

  public OIDCTokens getAdminServerAccessToken(String username, String password) throws AuthException {
    return getAuthTokensByPassword(username, password, AuthOIDCClient.ResourceServer.rs_admin_server);
  }

  /**
   * Get id, access tokens by refresh token.
   *
   * @param refreshToken
   * @return
   * @throws AuthException
   */
  public OIDCTokens getAuthTokensByRefreshToken(RefreshToken refreshToken) throws AuthException {
    return getAuthTokensByRefreshToken(refreshToken, AuthOIDCClient.ResourceServer.rs_esxcloud);
  }

  public OIDCTokens getAdminServerAccessToken(RefreshToken refreshToken) throws AuthException {
    return getAuthTokensByRefreshToken(refreshToken, AuthOIDCClient.ResourceServer.rs_admin_server);
  }

  /**
   * Get id, access and refresh tokens by username and password.
   *
   * @return AccessToken.
   */
  private OIDCTokens getAuthTokensByPassword(
      String username,
      String password,
      AuthOIDCClient.ResourceServer resourceServer)
      throws AuthException {
    try {
      PasswordGrant passwordGrant = new PasswordGrant(username, password);
      TokenSpec tokenSpec = new TokenSpec.Builder().
          refreshToken(true).
          idTokenGroups(GroupMembershipType.NONE).
          accessTokenGroups(GroupMembershipType.FULL).
          resourceServers(Arrays.asList(resourceServer.toString())).build();

      return oidcClient.acquireTokens(passwordGrant, tokenSpec);
    } catch (OIDCClientException | OIDCServerException | TokenValidationException | SSLConnectionException e) {
      throw new AuthException("Failed to acquire tokens.", e);
    }
  }

  /**
   * Get id, access tokens by refresh token.
   *
   * @param refreshToken
   * @param resourceServer
   * @return
   */
  private OIDCTokens getAuthTokensByRefreshToken(RefreshToken refreshToken,
                                                 AuthOIDCClient.ResourceServer resourceServer) throws AuthException {

    try {
      RefreshTokenGrant refreshTokenGrant = new RefreshTokenGrant(refreshToken);
      TokenSpec tokenSpec = new TokenSpec.Builder()
          .idTokenGroups(GroupMembershipType.NONE)
          .accessTokenGroups(GroupMembershipType.FULL)
          .resourceServers(Arrays.asList(resourceServer.toString()))
          .build();

      return oidcClient.acquireTokens(refreshTokenGrant, tokenSpec);
    } catch (OIDCClientException | OIDCServerException | TokenValidationException | SSLConnectionException e) {
      throw new AuthException("Failed to acquire tokens.", e);
    }
  }

  /**
   * Checks if the passed accessToken is valid. If not, throws the AuthException.
   *
   * @param accessToken
   * @return ResourceServerAccessToken
   * @throws TokenValidationException
   */
  public ResourceServerAccessToken parseAccessToken(String accessToken)
      throws TokenValidationException {
    return ResourceServerAccessToken.build(
        accessToken,
        providerPublicKey,
        AuthOIDCClient.ResourceServer.rs_esxcloud.toString(),
        0);
  }
}
