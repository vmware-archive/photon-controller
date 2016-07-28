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
import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.api.model.AuthInfo;
import com.vmware.photon.controller.api.model.Deployment;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link DeploymentSecurityGroupFetcher}.
 */
public class DeploymentSecurityGroupFetcherTest {

  private DeploymentBackend backend;

  private DeploymentSecurityGroupFetcher fetcher;

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
      backend = mock(DeploymentBackend.class);

      authorizationObject = new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.DEPLOYMENT,
          TransactionAuthorizationObject.Strategy.SELF,
          "id");

      fetcher = new DeploymentSecurityGroupFetcher(backend);
    }

    /**
     * Tests that only transaction authorization objects of kind
     * DEPLOYMENT are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must be of 'kind' DEPLOYMENT.")
    public void testWrongTransactionAuthorizationObjectKind() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.VM));
    }

    /**
     * Tests that only transaction authorization objects with authorization strategy
     * SELF are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must have 'strategy' SELF.")
    public void testWrongTransactionAuthorizationObjectStartegy() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.DEPLOYMENT,
          TransactionAuthorizationObject.Strategy.PARENT,
          "id"));
    }

    @Test
    public void testInvalidDeploymentId() throws Throwable {
      doThrow(new DeploymentNotFoundException("id")).when(backend).toApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test(dataProvider = "NoDeploymentObject")
    public void testNoDeploymentIdAndNoDeploymentObject(List<Deployment> deploymentList) throws Throwable {
      doReturn(deploymentList).when(backend).getAll();
      authorizationObject.setId(null);

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @DataProvider(name = "NoDeploymentObject")
    Object[][] getNoDeploymentObjectParams() {
      return new Object[][] {
          {null},
          {new ArrayList<>()}
      };
    }

    @Test
    public void testNoDeploymentIdAndTwoDeploymentObjects() throws Throwable {
      List<Deployment> deploymentList = new ArrayList<>();
      deploymentList.add(new Deployment());
      deploymentList.add(new Deployment());
      doReturn(deploymentList).when(backend).getAll();

      authorizationObject.setId(null);

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testDeploymentMissingAuthObject() throws Throwable {
      Deployment deployment = new Deployment();
      doReturn(deployment).when(backend).toApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testDeploymentMissingSecurityGroups() throws Throwable {
      Deployment deployment = new Deployment();
      deployment.setAuth(new AuthInfo());
      doReturn(deployment).when(backend).toApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testDeploymentWithSecurityGroups() throws Throwable {
      Deployment deployment = new Deployment();
      deployment.setAuth(new AuthInfo());
      deployment.getAuth().setSecurityGroups(ImmutableList.of("SG1", "SG2"));
      doReturn(deployment).when(backend).toApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(2));
      assertThat(groups, contains("SG1", "SG2"));
    }
  }
}
