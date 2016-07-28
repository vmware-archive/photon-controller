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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.Set;

/**
 * Tests {@link NoneSecurityGroupFetcher}.
 */
public class NoneSecurityGroupFetcherTest {
  private NoneSecurityGroupFetcher fetcher;

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

      authorizationObject = new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.NONE);

      fetcher = new NoneSecurityGroupFetcher();
    }

    /**
     * Tests that only transaction authorization objects of kind
     * NONE are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must be of 'kind' NONE.")
    public void testWrongTransactionAuthorizationObjectKind() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.VM));
    }

    @Test
    public void testSuccess() throws Throwable {
      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(1));
      assertThat(groups, contains(SecurityGroupFetcher.EVERYONE));
    }
  }
}
