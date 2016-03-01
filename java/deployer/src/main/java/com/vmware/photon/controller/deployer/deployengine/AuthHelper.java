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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.auth.AuthOIDCClient;

import org.bouncycastle.operator.OperatorCreationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * Helper class for auth related work.
 */
public class AuthHelper {
  /**
   * Get the redirect URI that goes to lotus to do the authentication.
   *
   * @param tenant                Lotus tenant.
   * @param user                  Lotus user name.
   * @param password              Lotus password.
   * @param authServerAddress     Auth server address.
   * @param authServerPort        Auth server port.
   * @param loginRedirectEndpoint Tell lotus where to redirect the request after successful login.
   * @param logoutRedirectEndpoint Tell lotus where to redirect the request after logout.
   * @return The redirect uri to lotus when authentication is needed.
   */
  public AuthClientHandler.ImplicitClient getResourceLoginUri(
      String tenant, String user, String password, String authServerAddress, int authServerPort,
      String loginRedirectEndpoint, String logoutRedirectEndpoint)
      throws AuthException, NoSuchAlgorithmException, CertificateException, OperatorCreationException,
      URISyntaxException {

    AuthOIDCClient oidcClient = new AuthOIDCClient(authServerAddress, authServerPort, tenant);
    return getResourceLoginUri(oidcClient, user, password, loginRedirectEndpoint, logoutRedirectEndpoint);
  }

  /**
   * Get the redirect URI that goes to lotus to do the authentication.
   *
   * @param oidcClient       OIDC client.
   * @param user             Lotus user name.
   * @param password         Lotus password.
   * @param loginRedirectEndpoint Tell lotus where to redirect the request after successful login.
   * @param logoutRedirectEndpoint Tell lotus where to redirect the request after logout.
   * @return The redirect uri to lotus when authentication is needed.
   */
  public AuthClientHandler.ImplicitClient getResourceLoginUri(
      AuthOIDCClient oidcClient, String user, String password,
      String loginRedirectEndpoint, String logoutRedirectEndpoint)
      throws AuthException, NoSuchAlgorithmException, CertificateException, OperatorCreationException,
      URISyntaxException {

    AuthClientHandler authClientHandler = oidcClient.getClientHandler(user, password);

    return authClientHandler.registerImplicitClient(new URI(loginRedirectEndpoint), new URI(logoutRedirectEndpoint));
  }
}
