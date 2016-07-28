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
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.resources.routes.AuthRoutes;
import com.vmware.photon.controller.apife.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Tests {@link BasicPolicyProvider}.
 */
public class BasicPolicyProviderTest {
  private static final String NON_ADMIN_ROUTE = "/v1/test-non-admin-route";

  private static final String ADMIN_ONLY_ROUTE = "/v1/test-admin-route";
  private static final String ADMIN_ONLY_ROUTE_WITH_RESTRICTION = "/v1/test-other-admin-route";
  private static final BasicPolicyProvider.PolicyRule[] RULES = {
      new BasicPolicyProvider.PolicyRule(ADMIN_ONLY_ROUTE, null),
      new BasicPolicyProvider.PolicyRule(
          ADMIN_ONLY_ROUTE_WITH_RESTRICTION, Pattern.compile("post|delete", Pattern.CASE_INSENSITIVE))
  };

  private static final Object[][] ADMIN_REQUEST_DATA = {
      {ADMIN_ONLY_ROUTE.substring(1), "GET"},
      {ADMIN_ONLY_ROUTE.substring(1).toUpperCase(), "GET"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "POST"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "post"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "Post"},
  };

  private static final Object[][] NON_ADMIN_REQUEST_DATA = {
      {NON_ADMIN_ROUTE.substring(1), "GET"},
      {NON_ADMIN_ROUTE.substring(1).toLowerCase(), "GET"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "GET"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "get"},
      {ADMIN_ONLY_ROUTE_WITH_RESTRICTION.substring(1), "Get"},
  };

  private BasicPolicyProvider subject;

  /**
   * dummy test to have IntelliJ recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private void setUpCommon() {
    subject = spy(new BasicPolicyProvider());
    doReturn(RULES).when(subject).getAdminRestrictions();
  }

  /**
   * Tests for the Policy rules.
   */
  public class RuleConfigTest {
    @BeforeMethod
    private void setUp() {
      subject = new BasicPolicyProvider();
    }

    /**
     * Tests properties of the rules that can be verified statically.
     */
    @Test
    public void testProperties() {
      for (BasicPolicyProvider.PolicyRule rule : subject.getAdminRestrictions()) {
        if (null == rule.allowedMethods) {
          continue;
        }

        assertThat(
            "pattern invalid for rule: " + rule.routePrefix,
            rule.allowedMethods.flags() & Pattern.CASE_INSENSITIVE,
            is(Pattern.CASE_INSENSITIVE));
      }
    }
  }

  /**
   * Tests the isOpenAccessRoute method.
   */
  public class IsOpenAccessRouteTest {
    ContainerRequest request;


    @BeforeMethod
    public void setUp() {
      setUpCommon();

      request = mock(ContainerRequest.class);
    }

    @Test(dataProvider = "TrueData")
    public void testTrue(String path) {
      doReturn(path).when(request).getPath(true);

      assertThat(subject.isOpenAccessRoute(request), is(true));
    }

    @DataProvider(name = "TrueData")
    Object[][] getTrueData() {
      return new Object[][]{
          {AuthRoutes.API.substring(1)},
          {AuthRoutes.API.substring(1).toUpperCase()},
          {"api"},
          {"API"},
          {"available"},
          {"AVAILABLE"}
      };
    }

    @Test(dataProvider = "FalseData")
    public void testFalse(String path) {
      doReturn(path).when(request).getPath(true);

      assertThat(subject.isOpenAccessRoute(request), is(false));
    }

    @DataProvider(name = "FalseData")
    Object[][] getFalseData() {
      return new Object[][]{
          {HostResourceRoutes.API.substring(1)},
          {DeploymentResourceRoutes.API.substring(1)}
      };
    }
  }

  /**
   * Tests for the checkAccessPermissions method.
   */
  public class CheckAccessPermissionsTest {
    ContainerRequest request;
    ResourceServerAccessToken token;

    @BeforeMethod
    public void setUp() throws Exception {
      setUpCommon();

      request = mock(ContainerRequest.class);
      token = AuthTestHelper.generateResourceServerAccessToken(Collections.<String>emptyList());
    }

    @Test(dataProvider = "NonAdminRouteNonAdminTokenData")
    public void testNonAdminRouteNonAdminToken(String path, String method) throws Throwable {
      doReturn(path).when(request).getPath(true);
      doReturn(method).when(request).getMethod();
      token = AuthTestHelper.generateResourceServerAccessToken(ImmutableList.of("esxcloud\\bob"));

      subject.checkAccessPermissions(request, token);
      verify(request).getPath(true);
      verify(request).getMethod();
    }

    @DataProvider(name = "NonAdminRouteNonAdminTokenData")
    Object[][] getNonAdminRouteNonAdminTokenData() {
      return NON_ADMIN_REQUEST_DATA;
    }

    @Test(dataProvider = "NonAdminRouteAdminTokenData")
    public void testNonAdminRouteAdminToken(String path, String method) throws Throwable {
      doReturn(path).when(request).getPath(true);
      doReturn(method).when(request).getMethod();
      token = AuthTestHelper
          .generateResourceServerAccessToken(ImmutableList.of("esxcloud" + BasicPolicyProvider.ADMIN_GROUP));

      subject.checkAccessPermissions(request, token);
      verify(request).getPath(true);
      verify(request).getMethod();
    }

    @DataProvider(name = "NonAdminRouteAdminTokenData")
    Object[][] getNonAdminRouteAdminTokenData() {
      return NON_ADMIN_REQUEST_DATA;
    }

    /**
     * Tests when the request is for an admin route and an admin token is provided.
     *
     * @param path
     * @param method
     * @throws Throwable
     */
    @Test(dataProvider = "AdminRouteAdminTokenData")
    public void testAdminRouteAdminToken(String path, String method) throws Throwable {
      doReturn(path).when(request).getPath(true);
      doReturn(method).when(request).getMethod();
      token = AuthTestHelper
          .generateResourceServerAccessToken(ImmutableSet.of("esxcloud" + BasicPolicyProvider.ADMIN_GROUP));

      subject.checkAccessPermissions(request, token);
      verify(request).getPath(true);
      verify(request).getMethod();
    }

    @DataProvider(name = "AdminRouteAdminTokenData")
    Object[][] getAdminRouteAdminTokenData() {
      return ADMIN_REQUEST_DATA;
    }


    /**
     * Tests when the request is for an admin route and a non-admin token is provided.
     *
     * @param path
     * @param method
     * @throws Throwable
     */
    @Test(dataProvider = "AdminRouteNonAdminTokenData")
    public void testAdminRouteNonAdminToken(String path, String method) throws Throwable {
      doReturn(path).when(request).getPath(true);
      doReturn(method).when(request).getMethod();
      token = AuthTestHelper.generateResourceServerAccessToken(ImmutableList.of("esxcloud\\bob"));

      try {
        subject.checkAccessPermissions(request, token);
        fail("ExternalException should have been thrown");
      } catch (ExternalException e) {
        assertThat(e.getErrorCode(), is(ErrorCode.ACCESS_FORBIDDEN.getCode()));
      }
      verify(request).getPath(true);
      verify(request).getMethod();
    }

    @DataProvider(name = "AdminRouteNonAdminTokenData")
    Object[][] getAdminRouteNonAdminTokenData() {
      return ADMIN_REQUEST_DATA;
    }

    @Test
    public void testAdminRouteNullGroupToken() {
      doReturn(ADMIN_ONLY_ROUTE.substring(1)).when(request).getPath(true);

      try {
        subject.checkAccessPermissions(request, token);
        fail("ExternalException should have been thrown");
      } catch (ExternalException e) {
        assertThat(e.getErrorCode(), is(ErrorCode.ACCESS_FORBIDDEN.getCode()));
      }
      verify(request).getPath(true);
      verify(request).getMethod();
    }

    @Test
    public void testAdminRouteEmptyGroupToken() throws Exception {
      doReturn(ADMIN_ONLY_ROUTE.substring(1)).when(request).getPath(true);

      try {
        subject.checkAccessPermissions(request, token);
        fail("ExternalException should have been thrown");
      } catch (ExternalException e) {
        assertThat(e.getErrorCode(), is(ErrorCode.ACCESS_FORBIDDEN.getCode()));
      }
      verify(request).getPath(true);
      verify(request).getMethod();
    }
  }
}
