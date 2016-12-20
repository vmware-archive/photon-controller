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

import com.vmware.photon.controller.api.model.helpers.Validator;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;

/**
 * Tests {@link Auth}.
 */
public class AuthTest {

  private static final String JSON_FILE = "fixtures/auth.json";

  private static Auth createAuth() {
    Auth auth = new Auth();
    auth.setEnabled(true);
    auth.setEndpoint("10.146.64.236");
    auth.setPort(443);

    return auth;
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test
    public void testValidAuthInfo() {
      ImmutableList<String> violations = validator.validate(createAuth());
      assertThat(violations.isEmpty(), is(true));
    }

  }

  /**
   * Tests {@link AuthInfo#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "Auth{enabled=true, endpoint=10.146.64.236, port=443}";
      Auth auth = createAuth();
      assertThat(auth.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testAuth() throws IOException {
      Auth auth = createAuth();
      String json = jsonFixture(JSON_FILE);

      assertThat(asJson(auth), is(equalTo(json)));
      assertThat(fromJson(json, Auth.class), is(auth));
    }
  }
}
