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
import com.vmware.photon.controller.api.frontend.clients.ClusterFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.api.model.Cluster;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
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
 * Tests {@link ClusterSecurityGroupFetcher}.
 */
public class ClusterSecurityGroupFetcherTest {

  private ClusterFeClient clusterFeClient;
  private ProjectSecurityGroupFetcher projectFetcher;

  private ClusterSecurityGroupFetcher fetcher;

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
      clusterFeClient = mock(ClusterFeClient.class);
      projectFetcher = mock(ProjectSecurityGroupFetcher.class);
      fetcher = new ClusterSecurityGroupFetcher(clusterFeClient, projectFetcher);

      authorizationObject = new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.CLUSTER,
          TransactionAuthorizationObject.Strategy.PARENT,
          "id");
    }

    /**
     * Tests that only transaction authorization objects of kind
     * CLUSTER are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must be of 'kind' CLUSTER.")
    public void testWrongTransactionAuthorizationObjectKind() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.NONE));
    }

    @Test
    public void testInvalidId() throws Throwable {
      doThrow(new ClusterNotFoundException("id")).when(clusterFeClient).get("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testParentWithoutSecurityGroups() throws Throwable {
      authorizationObject.setStrategy(TransactionAuthorizationObject.Strategy.PARENT);

      Cluster cluster = new Cluster();
      cluster.setProjectId("project-id");
      doReturn(cluster).when(clusterFeClient).get("id");

      ArgumentCaptor<TransactionAuthorizationObject> captor =
          ArgumentCaptor.forClass(TransactionAuthorizationObject.class);
      doReturn(ImmutableSet.of()).when(projectFetcher).fetchSecurityGroups(captor.capture());

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
      assertThat(captor.getValue().getId(), is("project-id"));
    }

    @Test
    public void testParentWithSecurityGroups() throws Throwable {
      authorizationObject.setStrategy(TransactionAuthorizationObject.Strategy.PARENT);

      Cluster cluster = new Cluster();
      cluster.setProjectId("project-id");
      doReturn(cluster).when(clusterFeClient).get("id");

      ArgumentCaptor<TransactionAuthorizationObject> captor =
          ArgumentCaptor.forClass(TransactionAuthorizationObject.class);
      doReturn(ImmutableSet.of("SG1", "SG2"))
          .when(projectFetcher).fetchSecurityGroups(captor.capture());

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(2));
      assertThat(groups, contains("SG1", "SG2"));
      assertThat(captor.getValue().getId(), is("project-id"));
    }
  }
}
