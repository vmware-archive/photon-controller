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

import com.vmware.identity.openidconnect.client.AuthorizationGrant;
import com.vmware.identity.openidconnect.client.Issuer;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.ProviderMetadata;
import com.vmware.identity.openidconnect.client.RefreshToken;
import com.vmware.identity.openidconnect.client.RefreshTokenGrant;
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.TokenSpec;

import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.testng.Assert.assertEquals;

import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;

/**
 * Test AuthTokenHandler.
 */
@PrepareForTest({AuthTokenHandler.class, CertificateFactory.class, KeyStore.class,
    ResourceServerAccessToken.class})
public class AuthTokenHandlerTest extends PowerMockTestCase {
  private AuthTokenHandler tokenHandler;
  private OIDCTokens tokens;
  private OIDCClient oidcClient;

  @BeforeMethod
  public void setUp() {
    tokenHandler = new AuthTokenHandler(mock(OIDCClient.class), mock(RSAPublicKey.class), mock(ProviderMetadata.class));
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

    OIDCTokens returnedTokens = tokenHandler.getAuthTokensByRefreshToken(mock(RefreshToken.class));
    assertEquals(tokens, returnedTokens);
  }

  @Test
  public void verifyAccessTokenValidationSuccess() throws Exception {

    String testAccessToken = "dummyAccessToken";

    mockStatic(ResourceServerAccessToken.class);
    ResourceServerAccessToken token = mock(ResourceServerAccessToken.class);

    Mockito.when(
        ResourceServerAccessToken.build(anyString(), (RSAPublicKey) anyObject(), anyString(), (Issuer) anyObject())
    ).thenReturn(token);

    tokenHandler.parseAccessToken(testAccessToken);
  }
}
