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
import com.vmware.photon.controller.apife.auth.fetcher.MultiplexedSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.SecurityGroupFetcher;
import com.vmware.photon.controller.apife.config.AuthConfig;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Test AuthPolicyProvider.
 */
public class AuthPolicyProviderTest {
  private AuthPolicyProvider policyProvider;
  private TransactionAuthorizationObjectResolver resolver;
  private MultiplexedSecurityGroupFetcher fetcher;
  private AuthConfig config;

  /**
   * dummy test to have IntelliJ recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  @BeforeMethod
  public void setUpCommon() {
    resolver = mock(TransactionAuthorizationObjectResolver.class);
    fetcher = mock(MultiplexedSecurityGroupFetcher.class);

    config = new AuthConfig();
    config.setTenant("esxcloud");
    policyProvider = new AuthPolicyProvider(resolver, fetcher, config);
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

      assertThat(policyProvider.isOpenAccessRoute(request), is(true));
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

      assertThat(policyProvider.isOpenAccessRoute(request), is(false));
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

    TransactionAuthorizationObject authorizationObject;

    @BeforeMethod
    public void setUp() throws Exception {
      setUpCommon();

      request = mock(ContainerRequest.class);
      token = AuthTestHelper.generateResourceServerAccessToken(Collections.<String>emptySet());
      authorizationObject = mock(TransactionAuthorizationObject.class);

      doReturn(authorizationObject).when(resolver).evaluate(request);
    }

    @Test
    public void testFetcherReturnsEveryone() throws Throwable {
      doReturn(ImmutableSet.of(SecurityGroupFetcher.EVERYONE)).when(fetcher).fetchSecurityGroups(authorizationObject);

      policyProvider.checkAccessPermissions(request, token);
    }

    @Test
    public void testMatchDefaultAdminGroup() throws Throwable {
      doReturn(ImmutableSet.of()).when(fetcher).fetchSecurityGroups(authorizationObject);
      token = AuthTestHelper.generateResourceServerAccessToken(ImmutableSet.of(config.getTenant() + AuthPolicyProvider
          .DEFAULT_ADMIN_GROUP_NAME));

      policyProvider.checkAccessPermissions(request, token);
    }

    @Test(dataProvider = "GroupsInCommon")
    public void testGroupsInCommon(Set<String> fetcherSGs, List<String> tokenSGs) throws Throwable {
      doReturn(fetcherSGs).when(fetcher).fetchSecurityGroups(authorizationObject);

      token = AuthTestHelper.generateResourceServerAccessToken(fetcherSGs);
      policyProvider.checkAccessPermissions(request, token);
    }

    @DataProvider(name = "GroupsInCommon")
    private Object[][] getGroupsInCommonData() {
      return new Object[][]{
          {ImmutableSet.of("SG1"), ImmutableList.of("SG1")},
          {ImmutableSet.of("SG1", "SG3"), ImmutableList.of("SG1")},
          {ImmutableSet.of("SG1"), ImmutableList.of("SG1", "SG3")},
      };
    }

    @Test(dataProvider = "NoGroupsInCommon",
        expectedExceptions = ExternalException.class)
    public void testNoGroupsInCommon(Set<String> fetcherSGs, List<String> tokenSGs) throws Throwable {
      doReturn(fetcherSGs).when(fetcher).fetchSecurityGroups(authorizationObject);

      policyProvider.checkAccessPermissions(request, token);
    }

    @DataProvider(name = "NoGroupsInCommon")
    private Object[][] getNoGroupsInCommonData() {
      return new Object[][]{
          {ImmutableSet.of(), ImmutableList.of()},
          {ImmutableSet.of("SG1"), ImmutableList.of()},
          {ImmutableSet.of(), ImmutableList.of("SG2")},
          {ImmutableSet.of("SG1"), ImmutableList.of("SG2")},
      };
    }
  }
}
