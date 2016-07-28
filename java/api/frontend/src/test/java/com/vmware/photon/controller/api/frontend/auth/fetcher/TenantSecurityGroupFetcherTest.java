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

package com.vmware.photon.controller.api.frontend.auth.fetcher;

import com.vmware.photon.controller.api.frontend.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Set;

/**
 * Tests {@link TenantSecurityGroupFetcher}.
 */
public class TenantSecurityGroupFetcherTest {

  private TenantBackend backend;

  private TenantSecurityGroupFetcher fetcher;

  /**
   * Dummy method so that intellj recognizes this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {

  }

  /**
   * Tests the fetchSecurityGroup method.
   */
  public class FetchSecurityGroupsTest {

    private TransactionAuthorizationObject authorizationObject;

    @BeforeMethod
    private void setUp() {
      backend = mock(TenantBackend.class);

      authorizationObject = new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.TENANT,
          TransactionAuthorizationObject.Strategy.SELF,
          "id");

      fetcher = new TenantSecurityGroupFetcher(backend);
    }

    /**
     * Tests that only transaction authorization objects of kind
     * DEPLOYMENT are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must be of 'kind' TENANT.")
    public void testWrongTransactionAuthorizationObjectKind() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.NONE));
    }

    /**
     * Tests that only transaction authorization objects with authorization strategy
     * SELF are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must have 'strategy' SELF.")
    public void testWrongTransactionAuthorizationObjectStartegy() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.TENANT,
          TransactionAuthorizationObject.Strategy.PARENT,
          "id"));
    }

    @Test
    public void testInvalidId() throws Throwable {
      doThrow(new TenantNotFoundException("id")).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testWithoutSecurityGroups() throws Throwable {
      Tenant tenant = new Tenant();
      doReturn(tenant).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testWithSecurityGroups() throws Throwable {
      Tenant tenant = new Tenant();
      tenant.setSecurityGroups(ImmutableList.of(new SecurityGroup("SG1", true), new SecurityGroup("SG2", false)));
      doReturn(tenant).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(2));
      assertThat(groups, contains("SG1", "SG2"));
    }
  }
}
