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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.model.constraints.AuthDisabled;
import com.vmware.photon.controller.api.model.constraints.AuthEnabled;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tests {@link AuthConfigurationSpec}.
 */
public class AuthConfigurationSpecTest {

  private static final String AUTH_CONFIG_SPEC_JSON_FILE = "fixtures/auth-configuration-spec-with-data.json";
  private static final String EMPTY_AUTH_CONFIG_SPEC_JSON_FILE = "fixtures/auth-configuration-spec-no-data.json";

  private AuthConfigurationSpec sampleEnabledAuthConfig = new AuthConfigurationSpecBuilder()
      .enabled(true)
      .tenant("t")
      .password("p")
      .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
      .build();

  private AuthConfigurationSpec sampleDisabledAuthConfig = new AuthConfigurationSpecBuilder()
      .enabled(false)
      .build();

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private final String[] authEnabledErrorMsgs = new String[]{
        "password may not be null (was null)",
        "tenant may not be null (was null)",
    };
    private final String[] authDisabledErrorMsgs = new String[]{
        "tenant must be null (was t)",
        "password must be null (was p)",
    };
    private Validator validator = new Validator();

    @Test(dataProvider = "validAuthConfig")
    public void testValidAuthConfig(AuthConfigurationSpec authConfig) {
      ImmutableList<String> violations = validator.validate(authConfig);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validAuthConfig")
    public Object[][] getValidAuthConfig() {
      return new Object[][]{
          {sampleEnabledAuthConfig},
          {sampleDisabledAuthConfig}
      };
    }

    @Test(dataProvider = "invalidAuthConfig")
    public void testInvalidAuthInfo(AuthConfigurationSpec authConfig, String[] errorMsgs) {
      ImmutableList<String> violations;
      if (authConfig.getEnabled()) {
        violations = validator.validate(authConfig, AuthEnabled.class);
      } else {
        violations = validator.validate(authConfig, AuthDisabled.class);
      }

      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
    }

    @DataProvider(name = "invalidAuthConfig")
    public Object[][] getInvalidAuthConfig() {
      return new Object[][]{
          {new AuthConfigurationSpecBuilder()
              .enabled(true)
              .securityGroups(Arrays.asList(new String[]{"adminGroup1"}))
              .build(),
              authEnabledErrorMsgs},
          {new AuthConfigurationSpecBuilder()
              .enabled(false)
              .tenant("t")
              .password("p")
              .securityGroups(null)
              .build(),
              authDisabledErrorMsgs}
      };
    }

    @Test(dataProvider = "invalidSecurityGroups")
    public void testInvalidSecurityGroups(AuthConfigurationSpec authConfig, String errorMsg, Class cls) {
      ImmutableList<String> violations = validator.validate(authConfig, cls);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }

    @DataProvider(name = "invalidSecurityGroups")
    public Object[][] getInvalidSecurityGroupsInfo() {
      return new Object[][]{
          {new AuthConfigurationSpecBuilder()
              .enabled(true)
              .tenant("t")
              .password("p")
              .securityGroups(new ArrayList<>())
              .build(),
              "securityGroups size must be between 1 and 2147483647 (was [])",
              AuthEnabled.class},
          {new AuthConfigurationSpecBuilder()
              .enabled(true)
              .tenant("t")
              .password("p")
              .securityGroups(null)
              .build(),
              "securityGroups may not be null",
              AuthEnabled.class},
          {new AuthConfigurationSpecBuilder()
              .enabled(false)
              .securityGroups(new ArrayList<>())
              .build(),
              "securityGroups must be null",
              AuthDisabled.class}
      };
    }
  }

  /**
   * Tests {@link AuthInfo#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "AuthConfigurationSpec{enabled=true, tenant=t, securityGroups=adminGroup1,adminGroup2}";
      assertThat(sampleEnabledAuthConfig.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testEmptyAuthConfig() throws IOException {
      AuthConfigurationSpec authConfig = new AuthConfigurationSpec();
      String json = JsonHelpers.jsonFixture(EMPTY_AUTH_CONFIG_SPEC_JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(authConfig), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, AuthConfigurationSpec.class), is(authConfig));
    }

    @Test
    public void testAuthConfig() throws IOException {
      String json = JsonHelpers.jsonFixture(AUTH_CONFIG_SPEC_JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(sampleEnabledAuthConfig), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, AuthConfigurationSpec.class), is(sampleEnabledAuthConfig));
    }
  }
}
