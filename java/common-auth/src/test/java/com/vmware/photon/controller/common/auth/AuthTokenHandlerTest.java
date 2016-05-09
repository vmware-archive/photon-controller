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
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.common.AuthorizationGrant;
import com.vmware.identity.openidconnect.common.RefreshToken;
import com.vmware.identity.openidconnect.common.RefreshTokenGrant;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
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
  private static String jwtString = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJBZG1pbmlzdHJhdG9yQGVzeGNsb3VkIiwiYXV" +
      "kIjoiQWRtaW5pc3RyYXRvckBlc3hjbG91ZCIsInNjb3BlIjoib3BlbmlkIG9mZmxpbmVfYWNjZXNzIHJzX2FkbWluX3NlcnZlciIsIm" +
      "lzcyI6Imh0dHBzOlwvXC8xMC4xNDYuNjQuMjM4XC9vcGVuaWRjb25uZWN0XC9lc3hjbG91ZCIsInRva2VuX2NsYXNzIjoicmVmc" +
      "mVzaF90b2tlbiIsInRva2VuX3R5cGUiOiJCZWFyZXIiLCJleHAiOjE0NjI5NDExMTcsImlhdCI6MTQ2MjkxOTUxNywidGVuYW50Ij" +
      "oiZXN4Y2xvdWQiLCJqdGkiOiI2MW42aE1ndXQxRkp2SkVPRU8wYjZ2NFBJT2NyeVozWlItVDNkc3ZfSUowIn0.J7Yd5wFdGRjJ10T" +
      "PfoTLu-RV-c2_fY3wQV1CiPhQVdhJ3A_qjZ3rJa7OakdH97lQguPytnjJXCuEV0lqdmqm64QGegDfymTuo4ogCxuMXkbADYDmBfv" +
      "YK4gfzxzpJymDrrW-FTup5YAdnQRsMUyLVvhHL5k84Hj6L0z_1ICkFOK72HpRMM_wnpZqy-7VmLNgOR4lwG3ShQutppGopoFquo04" +
      "3sX3o2a2MJhwo40Qu2xYQBO88WSjzTRJdVFaY_DocWL2j4w0bkASH9gcCXGkt9jjEvs2xcrwYMW7jlvVdo2tQEjRbsp8XWDXtpKpw" +
      "Us4_uBIz85ypxGJT9PfKfpcBNIKLKYivrl8aEnT6P0kWBSZbIp8lz1DQCrWHvWWmFPPiddr2imxCrna30puahfIDIknqc5cmXl8pj" +
      "RyvTVIjioU7vUq7C9Sx3iMwSZDkEad87q70K9zTA4h3rlVkwklpWJ3n5THA-FDcpdRuZ2Iwm5dH00EfJJepiB4y1ZIbavYb5L9-tz" +
      "WRanY5u3znsst3HaUWqubQvHT3O17rIce0mCQ6-Iep7_arKQJGed2RsR75AvyhL9X1AQVmeTfMjeg7ClrfBmQuj6skV5hDDhq1jmD" +
      "kZvuGQ-tw4uxfa4CErClFCwtlnimp8H8CY7gsrHx5kVBX3Z222NlxoJAImY3q9o";
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

    RefreshToken refreshToken = RefreshToken.parse(jwtString);
    OIDCTokens returnedTokens = tokenHandler.getAuthTokensByRefreshToken(refreshToken);
    assertEquals(tokens, returnedTokens);
  }
}
