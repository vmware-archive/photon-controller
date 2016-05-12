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

package com.vmware.photon.controller.apife.auth;

import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.TokenValidationError;
import com.vmware.identity.openidconnect.client.TokenValidationException;
import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.auth.fetcher.MultiplexedSecurityGroupFetcher;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.helpers.JerseyPropertiesDelegate;
import com.vmware.photon.controller.apife.helpers.JerseySecurityContext;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.common.auth.AuthTokenHandler;

import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.AssertJUnit.fail;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

/**
 * Test AuthFilter.
 */
public class AuthFilterTest {
  private static final String AUTH_SERVER_ADDRESS = "http://foo/url";
  private static final int AUTH_SERVER_PORT = 443;
  private static final String TENANT = "esxcloud";
  private static final String SHARED_SECRET = "shared-secret";

  private AuthFilter subject;

  @BeforeMethod
  private void setUp() {
    AuthConfig config = new AuthConfig();
    config.setAuthServerAddress(AUTH_SERVER_ADDRESS);
    config.setAuthServerPort(AUTH_SERVER_PORT);
    config.setTenant(TENANT);
    config.setSharedSecret(SHARED_SECRET);

    AuthPolicyProvider provider = new AuthPolicyProvider(
        mock(TransactionAuthorizationObjectResolver.class),
        mock(MultiplexedSecurityGroupFetcher.class),
        new AuthConfig());
    subject = new AuthFilter(config, provider);
  }

  @Test(dataProvider = "OpenApiData")
  public void testOpenApi(String path) throws Throwable {
    ContainerRequest request = buildRequest(path, buildHeadersWithToken());
    try {
      subject.filter(request);
    } catch (Exception e) {
      fail("Should not have thrown an exception");
    }
  }

  @DataProvider(name = "OpenApiData")
  Object[][] getOpenApiData() {
    return new Object[][]{
        {AuthRoutes.API.substring(1)},
        {"api"}
    };
  }

  @Test
  public void testMissingAuthToken() throws URISyntaxException {
    try {
      subject.filter(buildRequest("", new MultivaluedHashMap<>()));
      fail("Exception expected");
    } catch (WebApplicationException e) {
      assertThat(Response.Status.UNAUTHORIZED.getStatusCode(), is(e.getResponse().getStatus()));
      assertThat(ErrorCode.MISSING_AUTH_TOKEN.getCode(), is(((ApiError) e.getResponse().getEntity()).getCode()));
    }
  }

  @Test
  public void testExpiredToken() throws Throwable {
    TokenValidationException error = mock(TokenValidationException.class);
    doReturn(TokenValidationError.EXPIRED_TOKEN).when(error).getTokenValidationError();

    AuthTokenHandler handler = mock(AuthTokenHandler.class);
    doThrow(error).when(handler).parseAccessToken(any(String.class));

    subject.setTokenHandler(handler);

    try {
      subject.filter(buildRequest(HostResourceRoutes.API, buildHeadersWithToken()));
      fail("Exception expected");
    } catch (WebApplicationException e) {
      assertThat(Response.Status.UNAUTHORIZED.getStatusCode(), is(e.getResponse().getStatus()));
      assertThat(ErrorCode.EXPIRED_AUTH_TOKEN.getCode(), is(((ApiError) e.getResponse().getEntity()).getCode()));
    }
  }

  @Test
  public void testUnAuthorizedAccess() throws Throwable {
    ResourceServerAccessToken token = AuthTestHelper.generateResourceServerAccessToken(Collections.<String>emptySet());
    ContainerRequest request = buildRequest("", buildHeadersWithToken());

    AuthTokenHandler handler = mock(AuthTokenHandler.class);
    doReturn(token).when(handler).parseAccessToken(any(String.class));

    PolicyProvider policyProvider = mock(PolicyProvider.class);
    doThrow(new ExternalException(ErrorCode.ACCESS_FORBIDDEN))
        .when(policyProvider).checkAccessPermissions(request, token);

    subject.setTokenHandler(handler);
    subject.setPolicyProvider(policyProvider);

    try {
      subject.filter(request);
      fail("Exception expected");
    } catch (WebApplicationException e) {
      assertThat(
          Response.Status.FORBIDDEN.getStatusCode(),
          is(e.getResponse().getStatus()));
      assertThat(
          ErrorCode.ACCESS_FORBIDDEN.getCode(),
          is(((ApiError) e.getResponse().getEntity()).getCode()));
    }
  }

  @DataProvider(name = "ClusterApiData")
  Object[][] getClusterApiData() {
    return new Object[][]{
        {ClusterResourceRoutes.API},
        {ClusterResourceRoutes.CLUSTER_VMS_PATH.replace("{id}", "id")},
        {ClusterResourceRoutes.CLUSTERS_PATH.replace("{id}", "id")},
        {ClusterResourceRoutes.PROJECT_CLUSTERS_PATH.replace("{id}", "id")}
    };
  }

  @Test
  public void testServiceAuthAccess() throws Throwable {
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    headers.put(AuthFilter.AUTHORIZATION_HEADER, Arrays.asList(AuthFilter.AUTHORIZATION_METHOD + SHARED_SECRET));
    ContainerRequest request = buildRequest("", headers);

    try {
      subject.filter(request);
    } catch (WebApplicationException e) {
      fail("Exception not expected");
    }
  }

  private ContainerRequest buildRequest(String path, MultivaluedMap<String, String> headers) throws URISyntaxException {
    ContainerRequest containerRequest = new ContainerRequest(new URI(""), new URI(path), null, new
        JerseySecurityContext(), new JerseyPropertiesDelegate());
    for (String header : headers.keySet()) {
      containerRequest.getHeaders().put(header, headers.get(header));
    }
    return containerRequest;
  }

  private MultivaluedMap<String, String> buildHeadersWithToken() {
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    headers.put(AuthFilter.AUTHORIZATION_HEADER, Arrays.asList(AuthFilter.AUTHORIZATION_METHOD + "foo"));

    return headers;
  }
}
