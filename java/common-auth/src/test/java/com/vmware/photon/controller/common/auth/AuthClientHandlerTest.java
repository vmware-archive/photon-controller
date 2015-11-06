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
import com.vmware.identity.openidconnect.client.ClientAuthenticationMethod;
import com.vmware.identity.openidconnect.client.ClientID;
import com.vmware.identity.openidconnect.client.ClientInformation;
import com.vmware.identity.openidconnect.client.ClientRegistrationHelper;
import com.vmware.identity.openidconnect.client.IDToken;
import com.vmware.identity.openidconnect.client.Nonce;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.ResponseMode;
import com.vmware.identity.openidconnect.client.ResponseType;
import com.vmware.identity.openidconnect.client.State;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.client.TokenType;
import com.vmware.photon.controller.common.cert.X509CertificateHelper;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.testng.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 * Test AuthClientHandler.
 */
public class AuthClientHandlerTest {

  private AuthClientHandler clientHanler;
  private X509Certificate clientCertificate;
  private ClientRegistrationHelper clientRegistrationHelper;
  private AccessToken accessToken;
  private IDToken idToken;
  private AuthTokenHandler tokenHandler;
  private OIDCTokens tokens;
  private AuthOIDCClient authOidcClient;
  private OIDCClient oidcClient;
  private ClientID clientID;

  @BeforeMethod
  public void setUp() throws Exception {
    AuthCertificateStore certificateStore = new AuthCertificateStore();
    tokenHandler = mock(AuthTokenHandler.class);

    authOidcClient = mock(AuthOIDCClient.class);
    oidcClient = mock(OIDCClient.class);
    doReturn(oidcClient).when(authOidcClient).getOidcClient(any(ClientID.class));
    doReturn(oidcClient).when(authOidcClient).getOidcClient();

    clientRegistrationHelper = mock(ClientRegistrationHelper.class);
    clientHanler = spy(new AuthClientHandler(
        authOidcClient,
        clientRegistrationHelper,
        tokenHandler,
        AuthTestHelper.USER,
        AuthTestHelper.PASSWORD));
    accessToken = mock(AccessToken.class);


    accessToken = mock(AccessToken.class);
    idToken = mock(IDToken.class);
    tokens = mock(OIDCTokens.class);
    doReturn(accessToken).when(tokens).getAccessToken();
    doReturn(idToken).when(tokens).getIdToken();

    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    clientCertificate = x509CertificateHelper.generateX509Certificate();
    certificateStore.setCertificateEntry("client_certificate", clientCertificate);
  }

  @Test
  public void testRegisterClient() throws Exception {
    doReturn(tokens).when(tokenHandler).getAdminServerAccessToken(AuthTestHelper.USER, AuthTestHelper.PASSWORD);

    ClientInformation clientInformationMock = mock(ClientInformation.class);
    doReturn(clientInformationMock).when(clientRegistrationHelper).registerClient(
        eq(accessToken),
        eq(TokenType.BEARER),
        any(Set.class),
        any(URI.class),
        any(Set.class),
        eq(ClientAuthenticationMethod.NONE),
        any(String.class));

    ClientID mockClientID = mock(ClientID.class);
    doReturn(mockClientID).when(clientInformationMock).getClientId();

    ClientInformation clientInformation = clientHanler.registerClient(
        clientCertificate,
        new URI("https://redirect"));
    Assert.assertEquals(mockClientID, clientInformation.getClientId());
  }

  @Test
  public void testRegisterClientFailToGetToken() throws Exception {
    doThrow(new RuntimeException()).when(tokenHandler).getAdminServerAccessToken(
        AuthTestHelper.USER,
        AuthTestHelper.PASSWORD);

    ClientInformation clientInformationMock = mock(ClientInformation.class);
    doReturn(clientInformationMock).when(clientRegistrationHelper).registerClient(
        eq(accessToken),
        eq(TokenType.BEARER),
        any(Set.class),
        any(URI.class),
        any(Set.class),
        eq(ClientAuthenticationMethod.NONE),
        any(String.class));

    ClientID mockClientID = mock(ClientID.class);
    doReturn(mockClientID).when(clientInformationMock).getClientId();

    try {
      ClientInformation clientInformation = clientHanler.registerClient(
          clientCertificate,
          new URI("https://redirect"));
      fail("Expected exception.");
    } catch (AuthException e) {
    }
  }

  @Test
  public void testBuildAuthenticationRequestURI() throws URISyntaxException, OIDCClientException, AuthException {
    URI expectedResponse = new URI("foo");
    URI redirectUri = new URI("redirect");
    doReturn(expectedResponse).when(oidcClient).buildAuthenticationRequestURI(
        eq(redirectUri),
        any(ResponseType.class),
        any(ResponseMode.class),
        any(TokenSpec.class),
        any(State.class),
        any(Nonce.class));
    URI actualResponse = clientHanler.buildAuthenticationRequestURI(clientID, redirectUri);
    Assert.assertEquals(actualResponse, expectedResponse);
  }

  @Test
  public void testRegisterImplicitClient() throws Exception {
    doReturn(tokens).when(tokenHandler).getAdminServerAccessToken(AuthTestHelper.USER, AuthTestHelper.PASSWORD);
    doReturn(new URI("logout")).when(clientHanler).replaceIdTokenWithPlaceholder(any(URI.class));

    ClientInformation clientInformationMock = mock(ClientInformation.class);
    doReturn(clientInformationMock).when(clientRegistrationHelper).registerClient(
        eq(accessToken),
        eq(TokenType.BEARER),
        any(Set.class),
        any(URI.class),
        any(Set.class),
        eq(ClientAuthenticationMethod.NONE),
        any(String.class));

    ClientID mockClientID = mock(ClientID.class);
    doReturn(mockClientID).when(clientInformationMock).getClientId();

    URI expectedLoginResponse = new URI("login");
    URI loginRedirect = new URI("loginRedirect");
    URI logoutRedirect = new URI("logoutRedirect");
    doReturn(expectedLoginResponse).when(oidcClient).buildAuthenticationRequestURI(
        eq(loginRedirect),
        any(ResponseType.class),
        any(ResponseMode.class),
        any(TokenSpec.class),
        any(State.class),
        any(Nonce.class));

    URI expectedLogoutResponse = new URI("logout");
    doReturn(expectedLogoutResponse).when(oidcClient).buildLogoutRequestURI(eq(logoutRedirect), eq(idToken), any(State
        .class));

    AuthClientHandler.ImplicitClient implicitClient = clientHanler.registerImplicitClient(clientCertificate,
        loginRedirect, logoutRedirect);
    Assert.assertEquals(expectedLoginResponse.toString(), implicitClient.loginURI);
    Assert.assertEquals(expectedLogoutResponse.toString(), implicitClient.logoutURI);
  }

  @Test
  public void testReplaceIdTokenWithPlaceholderSuccess() throws Exception {

    String logoutURL = "https://10.146.39.99/openidconnect/logout/esxcloud?id_token_hint=eyJhbGciOiJSUzI1NiJ9.eyJzaWQ" +
        "iOiJhLVhxc2phS1NZYTdYQmVuRG55anlpRkFJUGNVbS1xMXJvUkszcG02TDdzIiwic3ViIjoiYWRtaW5pc3RyYXRvckBlc3hjbG91ZCIsIml" +
        "zcyI6Imh0dHBzOlwvXC8xMC4xNDYuMzkuOTlcL29wZW5pZGNvbm5lY3RcL2VzeGNsb3VkIiwiZ2l2ZW5fbmFtZSI6IkFkbWluaXN0cmF0b3I" +
        "iLCJpYXQiOjE0MzgzNzAwMzAsImV4cCI6MTQzODM4NDQzMCwidG9rZW5fY2xhc3MiOiJpZF90b2tlbiIsInRlbmFudCI6ImVzeGNsb3VkIiw" +
        "ibm9uY2UiOiIxIiwiYXVkIjoiYjM3MWU5ZDAtMGNiYi00MzM5LWE2MjQtZWNiZTI4MjcwNThmIiwiZmFtaWx5X25hbWUiOiJlc3hjbG91ZCI" +
        "sImp0aSI6IjhycVAwUzYybEFjS2dsV0VYNmhLa29kNmVrTl93ellfRFk5RzdhWlFrbnMiLCJ0b2tlbl90eXBlIjoiQmVhcmVyIn0.MowzDrk" +
        "7DPEv9T_a6F2xJFBwNljYnr7QSX5PjDYJ2pneRlhVELsRcI7Cqg0g4TSPKxfgFqg8KCVYTOm0gmGVt-K6zaxaTs3BkvbVOdEjLJY4RVGtEzG" +
        "PHZ4oLHcpWH-VKdZ_WGfnmTQ_8VlDj5aEwKClEDHIW4QG7Mai7WSdZwANhQrJ_T_ZpVQRKM7LffaHcPeTBgMZi5gWl6mAzGrY_5e4bLkw9FS" +
        "gJQeKSNSYeZ-c437yYvU1dmgzx2A2yR5fmbxnI3eAkNSWB9U5ZujHUntfp4sOcKNTnQKWJVbkRcZloj3cR1l_vw4MUGonD8Rt41MZBIUue5u" +
        "QRctg5rT2HaGVY7kL0dZpmp-9g6Q_SnTsr4oJ3tIMey19VjISx44FUzMHoEvJQgyI-E4BwcIrjoeoPaVchT1Qdbi-Zh5zKK9jGgoPqOjNeSv" +
        "sR5XVT7Xy857aXL8OFpZ8r4HSoZXT68vnfpqT_eQFdy59Sl6o-xG7_-OU1OUoiYB5OVP8ZqShajZ9kICD7m1SG3QYUnxqlW6I2JsMOsbVOPp" +
        "BRjPyHnf8k0CcV9ChjtIHHBHjXnh9woszu34_HCkie2n1pALG7AyEIOOdbAv33_rPcGfZJJhwycr4xXd-n6DYGtPDRgHmzKWDveFsLvXoV4t" +
        "9vW5HgKLFldBrLMPQgWUbpXzWWWs&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2Fapi%2Flogin-redirect.htm" +
        "l&state=E&correlation_id=EMhk6IVFwXs-wUrn90iYHA1aCULgP6sSMfomPcrw8xk";

    String logoutURLWithPlaceholder = "https://10.146.39.99/openidconnect/logout/esxcloud?id_token_hint=[ID_TOKEN_PLA" +
        "CEHOLDER]&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2Fapi%2Flogin-redirect.html&state=E&correlati" +
        "on_id=EMhk6IVFwXs-wUrn90iYHA1aCULgP6sSMfomPcrw8xk";

    Assert.assertEquals(new URI(logoutURLWithPlaceholder),
        clientHanler.replaceIdTokenWithPlaceholder(new URI(logoutURL)));
  }

  @DataProvider(name = "invalidLogoutURL")
  public Object[][] invalidLogoutURL() {
    return new Object[][]{
        {""},
        {"https://10.146.39.99/openidconnect/logout/esxcloud?"},
        {"https://10.146.39.99/openidconnect/logout/esxcloud?id_token_hint=eyJhb"},
        {"https://10.146.39.99/openidconnect/logout/esxcloud?&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2F" +
            "api%2Flogin-redirect.htm"},
    };
  }

  @Test(dataProvider = "invalidLogoutURL", expectedExceptions = IllegalArgumentException.class)
  public void testReplaceIdTokenWithPlaceholderWithInvalidLogoutUrl(String logoutURL) throws Exception {
    clientHanler.replaceIdTokenWithPlaceholder(new URI(logoutURL));
  }
}
