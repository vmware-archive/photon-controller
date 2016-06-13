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

package com.vmware.photon.controller.apife.auth;

import com.vmware.photon.controller.api.AuthConfigurationSpec;
import com.vmware.photon.controller.api.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link AuthConfigurationSpecValidator}.
 */
public class AuthConfigurationSpecValidatorTest {
  @Test(dataProvider = "validAuthConfig")
  public void testValidateSuccess(AuthConfigurationSpec authInfo) throws InvalidAuthConfigException {
    AuthConfigurationSpecValidator.validate(authInfo);
  }

  @DataProvider(name = "validAuthConfig")
  public Object[][] getValidAuthInfo() {
    return new Object[][]{
        {new AuthConfigurationSpecBuilder()
            .enabled(true)
            .tenant("t")
            .password("p")
            .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
            .build()},
        {new AuthConfigurationSpecBuilder().enabled(false).build()}
    };
  }

  @Test(dataProvider = "invalidAuthConfig")
  public void testInvalidAuthInfo(AuthConfigurationSpec authConfig, List<String> errorMsgs)
      throws InvalidAuthConfigException {
    try {
      AuthConfigurationSpecValidator.validate(authConfig);
      fail("Auth info validation should have failed");
    } catch (InvalidAuthConfigException e) {
      errorMsgs.stream().forEach(errorMsg -> assertTrue(e.getMessage().contains(errorMsg)));
    }
  }

  @DataProvider(name = "invalidAuthConfig")
  public Object[][] getInvalidAuthInfo() {
    return new Object[][]{
        {new AuthConfigurationSpecBuilder()
            .enabled(true)
            .securityGroups(Arrays.asList(new String[]{"adminGroup1"}))
            .build(),
            Arrays.asList(
                "tenant may not be null",
                "password may not be null")
            },
        {new AuthConfigurationSpecBuilder()
            .enabled(false)
            .tenant("t")
            .password("p")
            .securityGroups(null)
            .build(),
            Arrays.asList(
                "tenant must be null",
                "password must be null")
        }
    };
  }

  @Test(dataProvider = "invalidSecurityGroups")
  public void testInvalidSecurityGroups(AuthConfigurationSpec authConfig, String errorMsgs)
      throws InvalidAuthConfigException {
    try {
      AuthConfigurationSpecValidator.validate(authConfig);
      fail("Auth info validation should have failed");
    } catch (InvalidAuthConfigException e) {
      assertThat(e.getMessage(), containsString(errorMsgs));
    }
  }

  @DataProvider(name = "invalidSecurityGroups")
  public Object[][] getInvalidSecurityGroupsInfo() {
    return new Object[][]{
        {new AuthConfigurationSpecBuilder()
            .enabled(true)
            .tenant("t")
            .password("p")
            .securityGroups(Arrays.asList(new String[0]))
            .build(),
            "securityGroups size must be between 1 and 2147483647"},
        {new AuthConfigurationSpecBuilder()
            .enabled(true)
            .tenant("t")
            .password("p")
            .securityGroups(null)
            .build(),
            "securityGroups may not be null"},
        {new AuthConfigurationSpecBuilder()
            .enabled(false)
            .securityGroups(Arrays.asList(new String[0]))
            .build(),
            "securityGroups must be null"}
    };
  }
}
