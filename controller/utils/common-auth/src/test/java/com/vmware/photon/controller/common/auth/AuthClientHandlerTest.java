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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;

/**
 * Test AuthClientHandler.
 */
public class AuthClientHandlerTest{
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
            AuthClientHandler.replaceIdTokenWithPlaceholder(new URI(logoutURL)));
  }

  @DataProvider(name = "invalidLogoutURL")
  public Object[][] invalidLogoutURL() {
    return new Object[][]{
        {""},
        {"https://10.146.39.99/openidconnect/logout/esxcloud?"},
        {"https://10.146.39.99/openidconnect/logout/esxcloud?&post_logout_redirect_uri=https%3A%2F%2F10.118.97.239%2F" +
            "api%2Flogin-redirect.htm"},
    };
  }

  @Test(dataProvider = "invalidLogoutURL", expectedExceptions = IllegalArgumentException.class)
  public void testReplaceIdTokenWithPlaceholderWithInvalidLogoutUrl(String logoutURL) throws Exception {
    AuthClientHandler.replaceIdTokenWithPlaceholder(new URI(logoutURL));
  }
}
