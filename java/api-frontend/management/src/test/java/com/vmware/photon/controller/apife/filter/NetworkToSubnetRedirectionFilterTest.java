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

package com.vmware.photon.controller.apife.filter;

import com.vmware.photon.controller.apife.helpers.JerseyPropertiesDelegate;
import com.vmware.photon.controller.apife.helpers.JerseySecurityContext;
import com.vmware.photon.controller.apife.resources.routes.NetworkResourceRoutes;

import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests {@link NetworkToSubnetRedirectionFilter}.
 */
public class NetworkToSubnetRedirectionFilterTest {

  private NetworkToSubnetRedirectionFilter networkToSubnetRedirectionFilter;

  @BeforeMethod
  public void setUp() {
    this.networkToSubnetRedirectionFilter = new NetworkToSubnetRedirectionFilter();
  }

  @DataProvider(name = "NetworkAndSubnetRequests")
  Object[][] getSuccessfulRequests() {
    return new Object[][]{
        {"POST", UriBuilder.fromPath(NetworkResourceRoutes.API).build().toString()},
        {"GET", UriBuilder.fromPath(NetworkResourceRoutes.API).build().toString()},
        {"POST", UriBuilder.fromPath("/networks").build().toString()},
        {"GET", UriBuilder.fromPath("/networks").build().toString()},
    };
  }

  @Test(dataProvider = "NetworkAndSubnetRequests")
  public void testSuccess(String httpMethod, String path) throws Throwable {
    ContainerRequest request = buildRequest(path, httpMethod, new MultivaluedHashMap<>());
    this.networkToSubnetRedirectionFilter.filter(request);
    assertThat(request.getRequestUri().getPath().toLowerCase(), startsWith("subnet"));
  }

  private ContainerRequest buildRequest(String path, String method, MultivaluedMap<String, String> headers) throws
      URISyntaxException {
    ContainerRequest containerRequest = new ContainerRequest(new URI(""), new URI(path.replaceAll("^/", "")), method,
        new JerseySecurityContext(), new JerseyPropertiesDelegate());
    for (String header : headers.keySet()) {
      containerRequest.getHeaders().put(header, headers.get(header));
    }
    return containerRequest;
  }

}
