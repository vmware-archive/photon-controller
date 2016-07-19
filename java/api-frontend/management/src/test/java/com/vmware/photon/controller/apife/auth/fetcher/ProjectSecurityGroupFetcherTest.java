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

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.apife.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;

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
 * Tests {@link ProjectSecurityGroupFetcher}.
 */
public class ProjectSecurityGroupFetcherTest {

  private ProjectBackend backend;

  private ProjectSecurityGroupFetcher fetcher;

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
      backend = mock(ProjectBackend.class);
      fetcher = new ProjectSecurityGroupFetcher(backend);

      authorizationObject = new TransactionAuthorizationObject(
          TransactionAuthorizationObject.Kind.PROJECT,
          TransactionAuthorizationObject.Strategy.SELF,
          "id");
    }

    /**
     * Tests that only transaction authorization objects of kind
     * PROJECT are accepted.
     */
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "authorizationObject must be of 'kind' PROJECT.")
    public void testWrongTransactionAuthorizationObjectKind() {
      fetcher.fetchSecurityGroups(new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.NONE));
    }

    @Test
    public void testInvalidId() throws Throwable {
      doThrow(new ProjectNotFoundException("id")).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testSelfWithoutSecurityGroups() throws Throwable {
      Project project = new Project();
      doReturn(project).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testSelfWithSecurityGroups() throws Throwable {
      Project project = new Project();
      project.setSecurityGroups(ImmutableList.of(new SecurityGroup("SG1", true), new SecurityGroup("SG2", false)));
      doReturn(project).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(2));
      assertThat(groups, contains("SG1", "SG2"));
    }

    @Test
    public void testParentWithoutInheritedSecurityGroups() throws Throwable {
      authorizationObject.setStrategy(TransactionAuthorizationObject.Strategy.PARENT);

      Project project = new Project();
      project.setSecurityGroups(ImmutableList.of(new SecurityGroup("SG1", false), new SecurityGroup("SG2", false)));
      doReturn(project).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(0));
    }

    @Test
    public void testParentWithInheritedSecurityGroups() throws Throwable {
      authorizationObject.setStrategy(TransactionAuthorizationObject.Strategy.PARENT);

      Project project = new Project();
      project.setSecurityGroups(ImmutableList.of(new SecurityGroup("SG1", true), new SecurityGroup("SG2", false)));
      doReturn(project).when(backend).getApiRepresentation("id");

      Set<String> groups = fetcher.fetchSecurityGroups(authorizationObject);
      assertThat(groups.size(), is(1));
      assertThat(groups, contains("SG1"));
    }
  }
}
