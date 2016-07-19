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

import com.vmware.photon.controller.api.model.builders.AuthInfoBuilder;
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
 * Tests {@link AuthInfo}.
 */
public class AuthInfoTest {

  private static final String AUTH_INFO_JSON_FILE = "fixtures/auth-info-with-data.json";
  private static final String EMPTY_AUTH_INFO_JSON_FILE = "fixtures/auth-info-no-data.json";

  private AuthInfo sampleEnabledAuthInfo = new AuthInfoBuilder()
      .enabled(true)
      .endpoint("10.146.64.236")
      .port(443)
      .tenant("t")
      .username("u")
      .password("p")
      .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
      .build();
  private AuthInfo sampleDisabledAuthInfo = new AuthInfoBuilder().enabled(false).build();

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private final String[] authEnabledErrorMsgs = new String[]{
        "username may not be null (was null)",
        "password may not be null (was null)",
        "tenant may not be null (was null)",
    };
    private final String[] authDisabledErrorMsgs = new String[]{
        "tenant must be null (was t)",
        "username must be null (was u)",
        "password must be null (was p)",
    };
    private Validator validator = new Validator();

    @Test(dataProvider = "validAuthInfo")
    public void testValidAuthInfo(AuthInfo authInfo) {
      ImmutableList<String> violations = validator.validate(authInfo);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validAuthInfo")
    public Object[][] getValidAuthInfo() {
      return new Object[][]{
          {sampleEnabledAuthInfo},
          {sampleDisabledAuthInfo}
      };
    }

    @Test(dataProvider = "invalidAuthInfo")
    public void testInvalidAuthInfo(AuthInfo authInfo, String[] errorMsgs) {
      ImmutableList<String> violations;
      if (authInfo.getEnabled()) {
        violations = validator.validate(authInfo, AuthEnabled.class);
      } else {
        violations = validator.validate(authInfo, AuthDisabled.class);
      }

      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
    }

    @DataProvider(name = "invalidAuthInfo")
    public Object[][] getInvalidAuthInfo() {
      return new Object[][]{
          {new AuthInfoBuilder()
              .enabled(true)
              .securityGroups(Arrays.asList(new String[]{"adminGroup1"}))
              .build(),
              authEnabledErrorMsgs},
          {new AuthInfoBuilder()
              .enabled(false)
              .tenant("t")
              .username("u")
              .password("p")
              .securityGroups(null)
              .build(),
              authDisabledErrorMsgs}
      };
    }

    @Test(dataProvider = "invalidSecurityGroups")
    public void testInvalidSecurityGroups(AuthInfo authInfo, String errorMsg, Class cls) {
      ImmutableList<String> violations = validator.validate(authInfo, cls);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }

    @DataProvider(name = "invalidSecurityGroups")
    public Object[][] getInvalidSecurityGroupsInfo() {
      return new Object[][]{
          {new AuthInfoBuilder()
              .enabled(true)
              .tenant("t")
              .username("u")
              .password("p")
              .securityGroups(new ArrayList<>())
              .build(),
              "securityGroups size must be between 1 and 2147483647 (was [])",
              AuthEnabled.class},
          {new AuthInfoBuilder()
              .enabled(true)
              .tenant("t")
              .username("u")
              .password("p")
              .securityGroups(null)
              .build(),
              "securityGroups may not be null",
              AuthEnabled.class},
          {new AuthInfoBuilder()
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
          "AuthInfo{enabled=true, endpoint=10.146.64.236, port=443, " +
              "tenant=t, securityGroups=adminGroup1,adminGroup2}";
      assertThat(sampleEnabledAuthInfo.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testEmptyAuthInfo() throws IOException {
      AuthInfo authInfo = new AuthInfo();
      String json = JsonHelpers.jsonFixture(EMPTY_AUTH_INFO_JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(authInfo), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, AuthInfo.class), is(authInfo));
    }

    @Test
    public void testAuthInfo() throws IOException {
      String json = JsonHelpers.jsonFixture(AUTH_INFO_JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(sampleEnabledAuthInfo), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, AuthInfo.class), is(sampleEnabledAuthInfo));
    }
  }
}
