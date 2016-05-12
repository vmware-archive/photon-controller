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
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.common.AuthorizationGrant;
import com.vmware.identity.openidconnect.common.ClientID;
import com.vmware.identity.openidconnect.common.Issuer;
import com.vmware.identity.openidconnect.common.JWTID;
import com.vmware.identity.openidconnect.common.Nonce;
import com.vmware.identity.openidconnect.common.RefreshToken;
import com.vmware.identity.openidconnect.common.RefreshTokenGrant;
import com.vmware.identity.openidconnect.common.Scope;
import com.vmware.identity.openidconnect.common.SessionID;
import com.vmware.identity.openidconnect.common.Subject;
import com.vmware.identity.openidconnect.common.TokenType;

import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.testng.Assert.assertEquals;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Test AuthTokenHandler.
 */

public class AuthTokenHandlerTest extends PowerMockTestCase {
  private AuthTokenHandler tokenHandler;
  private OIDCTokens tokens;
  private OIDCClient oidcClient;

  @BeforeMethod
  public void setUp() {
    tokenHandler = new AuthTokenHandler(mock(OIDCClient.class), mock(RSAPublicKey.class));
    oidcClient = mock(OIDCClient.class);
  }

  @Test
  public void getAuthTokensByPasswordSuccess() throws Exception {
    doReturn(tokens).when(oidcClient).acquireTokens(any(AuthorizationGrant.class), any(TokenSpec.class));

    OIDCTokens returnedTokens = tokenHandler.getAuthTokensByPassword(AuthTestHelper.USER, AuthTestHelper.PASSWORD);
    assertEquals(tokens, returnedTokens);
  }

  @Test
  public void getAuthTokensByRefreshTokenSuccess() throws Exception {
    doReturn(tokens).when(oidcClient).acquireTokens(any(RefreshTokenGrant.class), any(TokenSpec.class));

    RefreshToken refreshToken = new RefreshToken(
        AuthTestHelper.privateKey,
        TokenType.BEARER,
        new JWTID(),
        new Issuer("iss"),
        new Subject("sub"),
        Arrays.asList("aud"),
        AuthTestHelper.issueTime,
        AuthTestHelper.expirationTime,
        Scope.OPENID,
        "tenant",
        (ClientID) null,
        (SessionID) null,
        AuthTestHelper.publicKey,
        (Subject) null,
        (Nonce) null);

    OIDCTokens returnedTokens = tokenHandler.getAuthTokensByRefreshToken(refreshToken);
    assertEquals(tokens, returnedTokens);
  }
}
