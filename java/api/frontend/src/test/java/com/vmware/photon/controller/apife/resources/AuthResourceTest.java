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

import com.vmware.photon.controller.api.model.Auth;
import com.vmware.photon.controller.apife.clients.DeploymentFeClient;
import com.vmware.photon.controller.apife.resources.auth.AuthResource;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.UriBuilder;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.auth.AuthResource}.
 */
public class AuthResourceTest extends ResourceTest {

  private static final boolean ENABLE_AUTH = true;
  private static final String AUTH_AUTH_SERVER_ADDRESS = "10.1.1.0";
  private static final int AUTH_AUTH_SERVER_PORT = 443;

  @Mock
  private DeploymentFeClient deploymentFeClient;

  private String authRoute = UriBuilder.fromPath(AuthRoutes.API).build().toString();

  @Override
  protected void setUpResources() throws Exception {
    addResource(new AuthResource(deploymentFeClient));
  }

  @Test
  public void dummy() {
  }

  /**
   * Contains tests for HTTP GET Method on AuthResource.
   */
  @Test
  public void testGetAuthInfo() throws Exception {
    Auth auth = new Auth();
    auth.setEnabled(AuthResourceTest.ENABLE_AUTH);
    auth.setEndpoint(AuthResourceTest.AUTH_AUTH_SERVER_ADDRESS);
    auth.setPort(AuthResourceTest.AUTH_AUTH_SERVER_PORT);

    when(deploymentFeClient.getAuth()).thenReturn(auth);

    Auth authInfo = client().target(authRoute).request().get(Auth.class);

    assertThat(authInfo.getEnabled(), is(AuthResourceTest.ENABLE_AUTH));
    assertThat(authInfo.getEndpoint(), is(AuthResourceTest.AUTH_AUTH_SERVER_ADDRESS));
    assertThat(authInfo.getPort(), is(AuthResourceTest.AUTH_AUTH_SERVER_PORT));
  }
}
