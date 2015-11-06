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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.Auth;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.ws.rs.core.UriBuilder;

/**
 * Tests {@link AuthResource}.
 */
public class AuthResourceTest extends ResourceTest {

  private static final boolean ENABLE_AUTH = true;
  private static final String AUTH_AUTH_SERVER_ADDRESS = "10.1.1.0";
  private static final int AUTH_AUTH_SERVER_PORT = 443;

  AuthResource authResource;
  String authRoute = UriBuilder.fromPath(AuthRoutes.API).build().toString();

  public AuthResourceTest() {
    AuthConfig config = new AuthConfig();
    config.setEnableAuth(ENABLE_AUTH);
    config.setAuthServerAddress(AUTH_AUTH_SERVER_ADDRESS);
    config.setAuthServerPort(AUTH_AUTH_SERVER_PORT);

    this.authResource = new AuthResource(config);
  }

  @Override
  protected void setUpResources() throws Exception {
    addResource(this.authResource);
  }

  @Test
  public void dummy() {
  }

  /**
   * Contains tests for HTTP GET Method on AuthResource.
   */
  public class GetAuthResourceTests {

    @BeforeMethod
    public void setUp() throws Exception {
      AuthResourceTest.this.setUpJersey();
    }

    @Test
    public void testGetAuthInfo() throws Exception {
      Auth authInfo = client().target(authRoute).request().get(Auth.class);

      assertThat(authInfo.getEnabled(), is(AuthResourceTest.ENABLE_AUTH));
      assertThat(authInfo.getEndpoint(), is(AuthResourceTest.AUTH_AUTH_SERVER_ADDRESS));
      assertThat(authInfo.getPort(), is(AuthResourceTest.AUTH_AUTH_SERVER_PORT));
    }
  }
}
