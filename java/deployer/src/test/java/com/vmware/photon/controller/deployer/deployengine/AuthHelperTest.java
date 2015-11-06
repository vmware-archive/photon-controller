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

import com.vmware.identity.openidconnect.client.ClientID;
import com.vmware.identity.openidconnect.client.ClientInformation;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.auth.AuthOIDCClient;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.testng.AssertJUnit.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link AuthHelper}.
 */
@PrepareForTest({ClientInformation.class})
public class AuthHelperTest {

  private AuthOIDCClient authOIDCClient;
  private AuthClientHandler authClientHandler;

  private ClientID clientID;
  private URI loginRedirectUri;
  private URI logoutRedirectUri;
  private ClientInformation clientInformation;
  AuthClientHandler.ImplicitClient implicitClient;

  private String tenantName = "tenant1";
  private String userName = "user1";
  private String password = "password1";
  private String authServerAddress = "lookupEndpoint1";
  private int authServerPort = 433;
  private String loginRedirectEndpoint = "loginRedirectEndpoint1";
  private String logoutRedirectEndpoint = "logoutRedirectEndpoint1";

  @BeforeMethod
  public void setUp() throws URISyntaxException, AuthException {
    clientID = new ClientID("client-id");
    loginRedirectUri = new URI(loginRedirectEndpoint);
    logoutRedirectUri = new URI(logoutRedirectEndpoint);

    authOIDCClient = mock(AuthOIDCClient.class);
    authClientHandler = mock(AuthClientHandler.class);

    Set<URI> redirectUris = new HashSet<>();
    redirectUris.add(loginRedirectUri);

    clientInformation = mock(ClientInformation.class);
    doReturn(clientID).when(clientInformation).getClientId();
    doReturn(redirectUris).when(clientInformation).getRedirectUris();

    implicitClient = new AuthClientHandler.ImplicitClient("client", "http://login", "http://logout");
  }

  @Test
  public void getRedirectUriTestSuccess() throws Throwable {
    doReturn(authClientHandler).when(authOIDCClient).getClientHandler(eq(userName), eq(password));
    doReturn(implicitClient).when(authClientHandler).registerImplicitClient(isA(X509Certificate.class),
            eq(loginRedirectUri), eq(logoutRedirectUri));

    AuthHelper authHelper = new AuthHelper();
    AuthClientHandler.ImplicitClient gotImplicitClient = authHelper.getResourceLoginUri(authOIDCClient,
        userName, password, loginRedirectEndpoint, logoutRedirectEndpoint);

    assertEquals(gotImplicitClient.loginURI, implicitClient.loginURI);
    assertEquals(gotImplicitClient.logoutURI, implicitClient.logoutURI);

    verify(authOIDCClient, times(1)).getClientHandler(userName, password);
    verify(authClientHandler, times(1)).registerImplicitClient(isA(X509Certificate.class),
        eq(loginRedirectUri), eq(logoutRedirectUri));
  }

  @Test(expectedExceptions = AuthException.class)
  private void getRedirectUriTestFailToGetClientHandler() throws Throwable {
    doThrow(new AuthException("Failed to get client hanlder."))
        .when(authOIDCClient)
        .getClientHandler(eq(userName), eq(password));

    AuthHelper authHelper = new AuthHelper();
    authHelper.getResourceLoginUri(authOIDCClient, userName, password,
        loginRedirectEndpoint, logoutRedirectEndpoint);

    verify(authOIDCClient, times(1)).getClientHandler(userName, password);
    verify(authClientHandler, never()).registerClient(isA(X509Certificate.class), eq(loginRedirectUri));
    verify(authClientHandler, never()).buildAuthenticationRequestURI(eq(clientID), eq(loginRedirectUri));
  }

  @Test(expectedExceptions = AuthException.class)
  private void getRedirectUriTestFailToRegister() throws Throwable {
    doReturn(authClientHandler).when(authOIDCClient).getClientHandler(eq(userName), eq(password));
    doThrow(new AuthException("Failed to build URI."))
            .when(authClientHandler)
            .registerImplicitClient(isA(X509Certificate.class), eq(loginRedirectUri), eq(logoutRedirectUri));

    AuthHelper authHelper = new AuthHelper();
    authHelper.getResourceLoginUri(authOIDCClient, userName, password,
        loginRedirectEndpoint, logoutRedirectEndpoint);

    verify(authOIDCClient, times(1)).getClientHandler(userName, password);
    verify(authClientHandler, times(1)).registerImplicitClient(isA(X509Certificate.class),
        eq(loginRedirectUri), eq(logoutRedirectUri));
  }
}
