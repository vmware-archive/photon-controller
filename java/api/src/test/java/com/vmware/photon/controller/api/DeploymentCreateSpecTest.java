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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.helpers.JsonHelpers;
import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link DeploymentCreateSpec}.
 */
public class DeploymentCreateSpecTest {
  private AuthInfo enabledAuthInfo = new AuthInfoBuilder()
      .enabled(true)
      .endpoint("10.146.64.236")
      .port(443)
      .tenant("t")
      .username("u")
      .password("p")
      .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
      .build();

  private AuthInfo disabledAuthInfo = new AuthInfoBuilder()
      .enabled(false)
      .build();

  private DeploymentCreateSpec createDeploymentCreateSpec(
      String imageDatastore,
      String syslogEndpoint,
      String ntpEndpoint) {

    return createDeploymentCreateSpec(imageDatastore, syslogEndpoint, ntpEndpoint, new AuthInfoBuilder().build());
  }

  private DeploymentCreateSpec createDeploymentCreateSpec(
      String imageDatastore,
      String syslogEndpoint,
      String ntpEndpoint,
      AuthInfo authInfo) {

    DeploymentCreateSpec spec = new DeploymentCreateSpec();

    spec.setImageDatastore(imageDatastore);
    spec.setSyslogEndpoint(syslogEndpoint);
    spec.setNtpEndpoint(ntpEndpoint);
    spec.setAuth(authInfo);

    return spec;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @DataProvider(name = "validDeployments")
    public Object[][] getValidDeployments() {
      return new Object[][]{
          {createDeploymentCreateSpec("i", "0.0.0.1", "0.0.0.2")},
          {createDeploymentCreateSpec("i", null, null)},
          {createDeploymentCreateSpec("i", "0.0.0.1", "0.0.0.2", enabledAuthInfo)},
          {createDeploymentCreateSpec("i", "0.0.0.1", "0.0.0.2", disabledAuthInfo)}
      };
    }

    @Test(dataProvider = "validDeployments")
    public void testValidDeployment(DeploymentCreateSpec spec) {
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "invalidDeployments")
    public Object[][] getInvalidDeployments() {
      return new Object[][]{
          {createDeploymentCreateSpec(null, "0.0.0.1", "0.0.0.2"),
              "imageDatastore may not be null (was null)"},
          {createDeploymentCreateSpec("", "0.0.0.1", "0.0.0.2"),
              "imageDatastore size must be between 1 and 2147483647 (was )"},
          {createDeploymentCreateSpec("i", "fake", "0.0.0.2"),
              "syslogEndpoint fake is invalid IP or Domain Address"},
          {createDeploymentCreateSpec("i", "0.0.0.2", "fake"),
              "ntpEndpoint fake is invalid IP or Domain Address"},
          {createDeploymentCreateSpec("i", "0.0.0.1", "0.0.0.2", null),
              "auth may not be null (was null)"}
      };
    }

    @Test(dataProvider = "invalidDeployments")
    public void testInvalidDeployments(DeploymentCreateSpec spec, String errorMsg) {
      List<String> violations = new ArrayList<>();

      violations.addAll(validator.validate(spec));
      if (spec.getAuth() != null) {
        violations.addAll(validator.validate(spec.getAuth()));
      }

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }

  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE_WITH_AUTH = "fixtures/deployment-create-spec-with-auth.json";
    private static final String JSON_FILE_WITHOUT_AUTH = "fixtures/deployment-create-spec-without-auth.json";

    @Test
    public void testSerializationWithAuth() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec("image-datastore", "0.0.0.1", "0.0.0.2", enabledAuthInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITH_AUTH);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }

    @Test
    public void testSerializationWithoutAuth() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec("image-datastore", "0.0.0.1", "0.0.0.2", disabledAuthInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITHOUT_AUTH);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }
  }
}
