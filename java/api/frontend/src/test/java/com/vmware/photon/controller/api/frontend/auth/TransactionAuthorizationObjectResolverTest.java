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

package com.vmware.photon.controller.api.frontend.auth;

import com.vmware.photon.controller.api.frontend.resources.routes.DeploymentResourceRoutes;

import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test for {@link TransactionAuthorizationObjectResolver}.
 */
public class TransactionAuthorizationObjectResolverTest {

  @Test(enabled = false)
  private void dummy() {

  }

  /**
   * Tests the patter matcher that is used to parse the request URLS.
   */
  public class UrlParserPatternTest {

    /**
     * Tests that we can parse a path of the form /v1/deployments.
     */
    @Test
    public void testResourceRootApi() {
      Matcher matcher = TransactionAuthorizationObjectResolver.URL_PARSER.matcher(
          DeploymentResourceRoutes.API);
      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.KIND_GROUP).toLowerCase(),
          is(DeploymentResourceRoutes.API.substring(1)));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ID_GROUP), isEmptyOrNullString());
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ACTION_GROUP), isEmptyOrNullString());
    }

    /**
     * Tests that we can parse a path of the form /v1/deployments/{id}.
     */
    @Test
    public void testResourceInstanceApi() {
      Matcher matcher = TransactionAuthorizationObjectResolver.URL_PARSER.matcher(
          DeploymentResourceRoutes.DEPLOYMENT_PATH);

      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.KIND_GROUP).toLowerCase(),
          is(DeploymentResourceRoutes.API.substring(1)));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ID_GROUP), is("{id}"));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ACTION_GROUP), isEmptyOrNullString());
    }

    /**
     * Tests that we can parse a path of the form v1/deployments/{id}/action.
     */
    @Test
    public void testResourceInstanceActionApi() {
      Matcher matcher = TransactionAuthorizationObjectResolver.URL_PARSER.matcher(
          DeploymentResourceRoutes.DEPLOYMENT_VMS_PATH.substring(1));

      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.KIND_GROUP).toLowerCase(),
          is(DeploymentResourceRoutes.API.substring(1)));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ID_GROUP), is("{id}"));
      assertThat(matcher.group(TransactionAuthorizationObjectResolver.ACTION_GROUP), is("vms"));
    }
  }

  /**
   * Tests the evaluate method.
   */
  public class EvaluateTest {
    private static final String ROOT_API = "foo";

    private Map<String, TransactionAuthorizationObjectResolver.Rule[]> rules;
    private ContainerRequest request;

    private TransactionAuthorizationObjectResolver subject;

    @BeforeClass
    private void setUpCommon() {
      rules = new HashMap<>();
      rules.put(
          ROOT_API,
          new TransactionAuthorizationObjectResolver.Rule[]{
              new TransactionAuthorizationObjectResolver.Rule(
                  Pattern.compile("get"),
                  TransactionAuthorizationObject.Kind.NONE),
              new TransactionAuthorizationObjectResolver.Rule(
                  Pattern.compile("delete"),
                  Pattern.compile("action"),
                  TransactionAuthorizationObject.Kind.CLUSTER,
                  TransactionAuthorizationObject.Strategy.PARENT),
              new TransactionAuthorizationObjectResolver.Rule(
                  Pattern.compile(".*"),
                  Pattern.compile("action2"),
                  TransactionAuthorizationObject.Kind.PROJECT),
          }
      );
    }

    @BeforeMethod
    private void setUp() {
      request = mock(ContainerRequest.class);
      subject = spy(new TransactionAuthorizationObjectResolver());
      doReturn(rules).when(subject).getEvaluationRules();
    }

    /**
     * Tests request path does not match expected pattern.
     */
    @Test
    public void testNoMatchOnPathParsing() {
      doReturn("/no-version/foo").when(request).getPath(true);

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.DEPLOYMENT));
      assertThat(object.getId(), isEmptyOrNullString());
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests request path that does not match any rule.
     */
    @Test
    public void testNoMatchOnPath() {
      doReturn("/v1/bar").when(request).getPath(true);

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.DEPLOYMENT));
      assertThat(object.getId(), isEmptyOrNullString());
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests request path that does not match any rule.
     */
    @Test
    public void testNoMatchOnRequestMethod() {
      doReturn(ROOT_API).when(request).getPath(true);
      doReturn("post").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.DEPLOYMENT));
      assertThat(object.getId(), isEmptyOrNullString());
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests that we can match a request path of form /v1/foo.
     */
    @Test
    public void testResourceRootApi() {
      doReturn(ROOT_API).when(request).getPath(true);
      doReturn("get").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.NONE));
      assertThat(object.getId(), isEmptyOrNullString());
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests that we can match a request path of form /v1/foo/{id}.
     */
    @Test
    public void testResourceInstanceApi() {
      doReturn(ROOT_API + "/id").when(request).getPath(true);
      doReturn("get").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.NONE));
      assertThat(object.getId(), is("id"));
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests that we can match a request path of form /v1/foo/{id}/action.
     */
    @Test
    public void testResourceInstanceActionApi() {
      doReturn(ROOT_API + "/id/action").when(request).getPath(true);
      doReturn("get").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.NONE));
      assertThat(object.getId(), is("id"));
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }

    /**
     * Tests that we can match a request path of form /v1/foo/{id}/action.
     */
    @Test
    public void testMatchSpecificMethodAndAction() {
      doReturn(ROOT_API + "/id/action").when(request).getPath(true);
      doReturn("delete").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.CLUSTER));
      assertThat(object.getId(), is("id"));
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.PARENT));
    }

    /**
     * Tests that we can match a request path of form /v1/foo/{id}/action.
     */
    @Test
    public void testMatchSpecificAction() {
      doReturn(ROOT_API + "/id/action2").when(request).getPath(true);
      doReturn("post").when(request).getMethod();

      TransactionAuthorizationObject object = subject.evaluate(request);
      assertThat(object.getKind(), is(TransactionAuthorizationObject.Kind.PROJECT));
      assertThat(object.getId(), is("id"));
      assertThat(object.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    }
  }
}
