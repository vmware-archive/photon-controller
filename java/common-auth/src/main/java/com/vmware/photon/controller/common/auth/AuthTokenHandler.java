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

import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCServerException;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.PasswordCredentialsGrant;
import com.vmware.identity.openidconnect.client.ProviderMetadata;
import com.vmware.identity.openidconnect.client.RefreshToken;
import com.vmware.identity.openidconnect.client.RefreshTokenGrant;
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.SSLConnectionException;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.client.TokenType;
import com.vmware.identity.openidconnect.client.TokenValidationException;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Authorization token retrieval logic.
 */
public class AuthTokenHandler {

  private final OIDCClient oidcClient;
  private final RSAPublicKey providerPublicKey;
  private final ProviderMetadata providerMetadata;

  /**
   * Constructor.
   */
  AuthTokenHandler(OIDCClient oidcClient, RSAPublicKey providerPublickKey, ProviderMetadata providerMetadata) {
    this.oidcClient = oidcClient;
    this.providerPublicKey = providerPublickKey;
    this.providerMetadata = providerMetadata;
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
      PasswordCredentialsGrant passwordGrant = new PasswordCredentialsGrant(username, password);
      TokenSpec tokenSpec = new TokenSpec.Builder(TokenType.BEARER).
          refreshToken(true).
          idTokenGroups(false).
          accessTokenGroups(true).
          resouceServers(Arrays.asList(resourceServer.toString())).build();

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
      TokenSpec tokenSpec = new TokenSpec.Builder(TokenType.BEARER)
          .idTokenGroups(false)
          .accessTokenGroups(true)
          .resouceServers(Arrays.asList(resourceServer.toString()))
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
        providerMetadata.getIssuer());
  }
}
