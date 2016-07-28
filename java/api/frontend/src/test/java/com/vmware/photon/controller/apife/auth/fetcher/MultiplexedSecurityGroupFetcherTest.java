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

package com.vmware.photon.controller.apife.auth.fetcher;

import com.vmware.photon.controller.apife.auth.TransactionAuthorizationObject;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests {@link MultiplexedSecurityGroupFetcher}.
 */
public class MultiplexedSecurityGroupFetcherTest {

  private MultiplexedSecurityGroupFetcher fetcher;

  /**
   * Dummy method so that intellJ recognizes this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    /**
     * Tests that an object can be instantiated successfully.
     */
    @Test
    public void testSuccess() {
      MultiplexedSecurityGroupFetcher fetcher = new MultiplexedSecurityGroupFetcher(
          mock(NoneSecurityGroupFetcher.class),
          mock(DeploymentSecurityGroupFetcher.class),
          mock(TenantSecurityGroupFetcher.class),
          mock(ProjectSecurityGroupFetcher.class),
          mock(ResourceTicketSecurityGroupFetcher.class),
          mock(ClusterSecurityGroupFetcher.class),
          mock(DiskSecurityGroupFetcher.class),
          mock(VmSecurityGroupFetcher.class));
      assertThat(fetcher, notNullValue());
      assertThat(fetcher.getFetcherMap().size(), is(8));
    }
  }

  /**
   * Tests for the fetchSecurityGroups method.
   */
  public class FetchSecurityGroupsTest {

    @BeforeMethod
    private void setUp() {
      Map<TransactionAuthorizationObject.Kind, SecurityGroupFetcher> map = new HashMap<>();
      map.put(TransactionAuthorizationObject.Kind.DEPLOYMENT, new TestFetcher());

      fetcher = new MultiplexedSecurityGroupFetcher(map);
    }

    /**
     * Tests that IllegalArgumentException is raised when no fetcher is registered for the authorization object type.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject of 'kind' NONE is not supported.")
    public void testObjectKindNotSupported() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.NONE));
    }

    /**
     * Tests that security groups can be retrieved successfully.
     */
    @Test
    public void testSuccess() {
      Set<String> securityGroups = fetcher.fetchSecurityGroups(
          new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.DEPLOYMENT));

      assertThat(securityGroups.size(), is(2));
      assertThat(securityGroups, contains("SG1", "SG2"));
    }

    /**
     * Implements a test version of the SecurityGroupFetcher interface.
     */
    private class TestFetcher implements SecurityGroupFetcher {
      @Override
      public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
        return ImmutableSet.of("SG1", "SG2");
      }
    }
  }
}
