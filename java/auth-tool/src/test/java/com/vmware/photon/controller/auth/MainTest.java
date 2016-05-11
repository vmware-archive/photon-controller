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

package com.vmware.photon.controller.auth;

import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.common.AccessToken;
import com.vmware.identity.openidconnect.common.RefreshToken;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthOIDCClient;
import com.vmware.photon.controller.common.auth.AuthTokenHandler;

import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.net.URI;

/**
 * Test {@link Main}.
 */
public class MainTest extends PowerMockTestCase {

  public static final String AUTH_SERVER_ADDRESS = "10.146.64.236";
  public static final int AUTH_SERVER_PORT = 443;
  public static final String LOGIN_REDIRECT_URL = "https://login-redirect";
  public static final String LOGOUT_REDIRECT_URL = "https://logout-redirect";
  public static final String USER = "root";
  public static final String PASSWORD = "lotus";
  public static final String TENANT = "tenant";
  public static final String REFRESH_TOKEN = "refresh-token";

  @Test(enabled = false)
  public void verifyGetAccessTokenByPassword() throws Exception {

    AuthOIDCClient oidcClient = mock(AuthOIDCClient.class);
    whenNew(AuthOIDCClient.class).withArguments(AUTH_SERVER_ADDRESS, AUTH_SERVER_PORT, TENANT)
        .thenReturn(oidcClient);

    AuthTokenHandler authHandler = mock(AuthTokenHandler.class);
    doReturn(authHandler).when(oidcClient).getTokenHandler();

    OIDCTokens tokens = mock(OIDCTokens.class);
    when(authHandler.getAuthTokensByPassword(USER, PASSWORD))
        .thenReturn(tokens);

    AccessToken token = mock(AccessToken.class);
    when(tokens.getAccessToken()).thenReturn(token);

    String tokenString = "dummyTestToken";
    when(token.serialize()).thenReturn(tokenString);

    Main.main(new String[]
        {"get-access-token", "-a", AUTH_SERVER_ADDRESS, "-n", Integer.toString(AUTH_SERVER_PORT), "-t", TENANT, "-u",
            USER, "-p", PASSWORD});
    verify(authHandler, times(1)).getAuthTokensByPassword(USER, PASSWORD);
  }

  @Test(enabled = false)
  public void verifyGetAccessTokenByRefreshToken() throws Exception {

    AuthOIDCClient oidcClient = mock(AuthOIDCClient.class);
    whenNew(AuthOIDCClient.class).withArguments(AUTH_SERVER_ADDRESS, AUTH_SERVER_PORT, TENANT)
        .thenReturn(oidcClient);

    AuthTokenHandler authHandler = mock(AuthTokenHandler.class);
    doReturn(authHandler).when(oidcClient).getTokenHandler();

    OIDCTokens tokens = mock(OIDCTokens.class);
    when(authHandler.getAuthTokensByRefreshToken(any(RefreshToken.class)))
        .thenReturn(tokens);

    AccessToken token = mock(AccessToken.class);
    when(tokens.getAccessToken()).thenReturn(token);

    String tokenString = "dummyTestToken";
    when(token.serialize()).thenReturn(tokenString);

    Main.main(new String[]
        {"get-access-token", "-a", AUTH_SERVER_ADDRESS, "-r", REFRESH_TOKEN});
    verify(authHandler, times(1)).getAuthTokensByRefreshToken(any(RefreshToken.class));
  }

  @Test(enabled = false)
  public void verifyGetRefreshToken() throws Exception {

    AuthOIDCClient oidcClient = mock(AuthOIDCClient.class);
    whenNew(AuthOIDCClient.class).withArguments(AUTH_SERVER_ADDRESS, AUTH_SERVER_PORT, TENANT)
        .thenReturn(oidcClient);

    AuthTokenHandler authHandler = mock(AuthTokenHandler.class);
    doReturn(authHandler).when(oidcClient).getTokenHandler();

    OIDCTokens tokens = mock(OIDCTokens.class);
    when(authHandler.getAuthTokensByPassword(USER, PASSWORD))
        .thenReturn(tokens);

    RefreshToken token = mock(RefreshToken.class);
    when(tokens.getRefreshToken()).thenReturn(token);

    String tokenString = "dummyTestToken";
    when(token.serialize()).thenReturn(tokenString);

    Main.main(new String[]
        {"get-refresh-token", "-a", AUTH_SERVER_ADDRESS, "-n", Integer.toString(AUTH_SERVER_PORT), "-t", TENANT,
            "-u", USER, "-p", PASSWORD});
    verify(authHandler, times(1)).getAuthTokensByPassword(USER, PASSWORD);
  }

  @Test(enabled = false)
  public void verifyRegisterClient() throws Exception {

    AuthOIDCClient oidcClient = mock(AuthOIDCClient.class);
    whenNew(AuthOIDCClient.class).withArguments(AUTH_SERVER_ADDRESS, AUTH_SERVER_PORT, TENANT).thenReturn(oidcClient);

    AuthClientHandler authClientHandler = mock(AuthClientHandler.class);
    when(oidcClient.getClientHandler(USER, PASSWORD)).thenReturn(authClientHandler);

    AuthClientHandler.ImplicitClient implicitClient = new AuthClientHandler.ImplicitClient("client-id", "login-url",
        "logout-url");
    when(authClientHandler.registerImplicitClient(eq(new URI(LOGIN_REDIRECT_URL)),
        eq(new URI(LOGOUT_REDIRECT_URL)))).thenReturn(implicitClient);

    Main.main(new String[]
        {"register-client", "-t", TENANT, "-u", USER, "-p", PASSWORD, "-a", AUTH_SERVER_ADDRESS,
            "-n", Integer.toString(AUTH_SERVER_PORT), "-r", LOGIN_REDIRECT_URL, "-o", LOGOUT_REDIRECT_URL});
    verify(authClientHandler, times(1)).registerImplicitClient(eq(new URI(LOGIN_REDIRECT_URL)),
        eq(new URI(LOGOUT_REDIRECT_URL)));
  }
}
