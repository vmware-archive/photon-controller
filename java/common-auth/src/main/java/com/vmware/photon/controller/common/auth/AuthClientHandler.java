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

import com.vmware.identity.openidconnect.client.AccessToken;
import com.vmware.identity.openidconnect.client.AdminServerException;
import com.vmware.identity.openidconnect.client.ClientAuthenticationMethod;
import com.vmware.identity.openidconnect.client.ClientID;
import com.vmware.identity.openidconnect.client.ClientInformation;
import com.vmware.identity.openidconnect.client.ClientRegistrationHelper;
import com.vmware.identity.openidconnect.client.IDToken;
import com.vmware.identity.openidconnect.client.Nonce;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.ResponseMode;
import com.vmware.identity.openidconnect.client.ResponseType;
import com.vmware.identity.openidconnect.client.ResponseValue;
import com.vmware.identity.openidconnect.client.SSLConnectionException;
import com.vmware.identity.openidconnect.client.State;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.client.TokenType;

import com.google.common.annotations.VisibleForTesting;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that handles oauth clients.
 */
public class AuthClientHandler {
  private static final String LOGIN_STATE = "L";
  private static final String LOGOUT_STATE = "E";
  private static final String LOGIN_REQUEST_NONCE = "1";
  private static final String LOGOUT_URL_ID_TOKEN_START = "id_token_hint=";
  private static final String LOGOUT_URL_ID_TOKEN_END = "&post_logout_redirect_uri=";
  private static final String LOGOUT_URL_ID_TOKEN_PLACEHOLDER = "[ID_TOKEN_PLACEHOLDER]";

  private final AuthOIDCClient oidcClient;
  private final String user;
  private final String password;
  private ClientRegistrationHelper clientRegistrationHelper;
  private AuthTokenHandler tokenHandler;

  /**
   * Constructor.
   *
   * @param oidcClient - Provides the oidc client.
   * @param user       - Provides the user of the Lotus Admin.
   * @param password   - Provides the password of the Lotus Admin.
   * @throws AuthException
   * @throws URISyntaxException
   */
  AuthClientHandler(
      AuthOIDCClient oidcClient,
      ClientRegistrationHelper clientRegistrationHelper,
      AuthTokenHandler tokenHandler,
      String user,
      String password)
      throws AuthException {
    this.oidcClient = oidcClient;
    this.clientRegistrationHelper = clientRegistrationHelper;
    this.tokenHandler = tokenHandler;
    this.user = user;
    this.password = password;
  }

  /**
   * Register implicit client and retrieve login and logout URIs.
   * @param clientX509Certificate
   * @param loginRedirectURI
   * @param logoutRedirectURI
   * @return
   * @throws AuthException
   */
  public ImplicitClient registerImplicitClient(X509Certificate clientX509Certificate, URI loginRedirectURI,
                                               URI logoutRedirectURI)
          throws AuthException {
    try {
      OIDCTokens tokens = tokenHandler.getAdminServerAccessToken(user, password);
      ClientInformation clientInformation = registerClient(clientX509Certificate, tokens.getAccessToken(),
          loginRedirectURI, loginRedirectURI, logoutRedirectURI);
      URI loginURI = buildAuthenticationRequestURI(clientInformation.getClientId(), loginRedirectURI);
      URI logoutURI = buildLogoutRequestURI(clientInformation.getClientId(), tokens.getIdToken(), logoutRedirectURI);
      return new ImplicitClient(clientInformation.getClientId().getValue(), loginURI.toString(), logoutURI.toString());
    } catch (Exception e) {
      throw new AuthException(String.format("Failed to register implicit client with loginRedirectURI %s and " +
          "logoutRedirectURI %s ", loginRedirectURI, logoutRedirectURI), e);
    }
  }

  /**
   * Register OAuth client.
   *
   * @param clientX509Certificate
   * @param redirectURI
   * @return
   * @throws AuthException
   */
  public ClientInformation registerClient(X509Certificate clientX509Certificate, URI redirectURI) throws
      AuthException {
    try {
      AccessToken accessToken = tokenHandler.getAdminServerAccessToken(user, password).getAccessToken();
      return registerClient(clientX509Certificate, accessToken, redirectURI, redirectURI, redirectURI);
    } catch (Exception e) {
      throw new AuthException("Failed to register client " + redirectURI, e);
    }
  }

  /**
   * Build authentication request URI for the given client. The client is expected to have exactly one redirect URI.
   */
  public URI buildAuthenticationRequestURI(ClientID clientID, URI redirectURI) throws AuthException {
    try {
      TokenSpec tokenSpec = new TokenSpec.Builder(TokenType.BEARER).
          refreshToken(false).
          idTokenGroups(false).
          accessTokenGroups(true).
          resouceServers(Arrays.asList(AuthOIDCClient.ResourceServer.rs_esxcloud.toString())).build();

      Set<ResponseValue> responseValues = new HashSet<>();
      responseValues.add(ResponseValue.ID_TOKEN);
      responseValues.add(ResponseValue.TOKEN);

      return oidcClient.getOidcClient(clientID)
          .buildAuthenticationRequestURI(
              redirectURI,
              new ResponseType(responseValues),
              ResponseMode.FRAGMENT,
              tokenSpec,
              new State(LOGIN_STATE),
              new Nonce(LOGIN_REQUEST_NONCE));
    } catch (OIDCClientException e) {
      throw new AuthException("Failed to build authentication request URI.", e);
    }
  }

  /**
   * Build logout request URI for the given client. The client is expected to have exactly one post-logout URI.
   */
  private URI buildLogoutRequestURI(ClientID clientID, IDToken idToken, URI postLogoutURI) throws AuthException,
      URISyntaxException {
      try {
          return replaceIdTokenWithPlaceholder(oidcClient.getOidcClient(clientID)
                  .buildLogoutRequestURI(postLogoutURI, idToken, new State(LOGOUT_STATE)));
      } catch (OIDCClientException e) {
          throw new AuthException("Failed to build logout URI", e);
      }
  }

  /**
   * Register OAuth client.
   */
  private ClientInformation registerClient(X509Certificate clientX509Certificate, AccessToken accessToken, URI
      redirectURI, URI logoutURI, URI postLogoutURI)
      throws AuthException, OIDCClientException, SSLConnectionException, AdminServerException {
      Exception registerException;
      try {
          return clientRegistrationHelper.registerClient(
                  accessToken,
                  TokenType.BEARER,
                  Collections.singleton(redirectURI),
                  logoutURI,
                  Collections.singleton(postLogoutURI),
                  ClientAuthenticationMethod.NONE,
                  null);
      } catch (AdminServerException e) {
          // And AdminServer error means that the client is already registered.
          registerException = e;
      }

      // Try to retrieve the already registered client.
      Collection<ClientInformation> clientList = clientRegistrationHelper.getAllClients(accessToken,
          TokenType.BEARER);
      for (ClientInformation client : clientList) {
          if (client.getRedirectUris().size() == 1 &&
                  client.getRedirectUris().iterator().next().compareTo(redirectURI) == 0) {
              return client;
          }
      }
      throw new AuthException("Client expected to be registered,  but not found", registerException);
  }

  /**
   * Replace the id_token part in logout URL with placeholder.
   *
   * @param logoutUri
   * @throws AuthException
   */
  @VisibleForTesting
  protected URI replaceIdTokenWithPlaceholder(URI logoutUri) throws URISyntaxException {
    String logoutStr = logoutUri.toString();
    int idTokenStartIndex = logoutStr.indexOf(LOGOUT_URL_ID_TOKEN_START) + LOGOUT_URL_ID_TOKEN_START.length();
    int idTokenEndIndex = logoutStr.lastIndexOf(LOGOUT_URL_ID_TOKEN_END);

    if (idTokenStartIndex <= LOGOUT_URL_ID_TOKEN_START.length() || idTokenEndIndex < 0 ||
        idTokenEndIndex < idTokenStartIndex) {
      throw new IllegalArgumentException(String.format("Logout URL %s has invalid format", logoutUri.toString()));
    }

    StringBuilder replacedLogoutBuilder = new StringBuilder(logoutStr.substring(0, idTokenStartIndex));
    replacedLogoutBuilder.append(LOGOUT_URL_ID_TOKEN_PLACEHOLDER);
    replacedLogoutBuilder.append(logoutStr.substring(idTokenEndIndex));
    return new URI(replacedLogoutBuilder.toString());
  }

  /**
   * Class encapsulating implicit client info.
   */
  public static class ImplicitClient {
      public final String clientID;
      public final String loginURI;
      public final String logoutURI;

      public ImplicitClient(String clientID, String loginURI, String logoutURI) {
          this.clientID = clientID;
          this.loginURI = loginURI;
          this.logoutURI = logoutURI;
      }
  }
}
