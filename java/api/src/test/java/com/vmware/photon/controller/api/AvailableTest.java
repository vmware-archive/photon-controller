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

import com.vmware.photon.controller.api.helpers.Validator;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;

/**
 * Tests {@link Available}.
 */
public class AvailableTest {

  private static final String JSON_FILE = "fixtures/available.json";

  private static Available createAvailable() {
    Available available = new Available();
    return available;
  }

  /**
   * Test that validation passes.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test
    public void testValidAvailable() {
      ImmutableList<String> violations = validator.validate(createAvailable());
      assertThat(violations.isEmpty(), is(true));
    }

  }

  /**
   * Tests {@link AuthInfo#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString = "Available{}";
      Available available = createAvailable();
      assertThat(available.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testAuth() throws IOException {
      Available available = createAvailable();
      String json = jsonFixture(JSON_FILE);

      assertThat(asJson(available), is(equalTo(json)));
      assertThat(fromJson(json, Available.class), is(available));
    }
  }
}
