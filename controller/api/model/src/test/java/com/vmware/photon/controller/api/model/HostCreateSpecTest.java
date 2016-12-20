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

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests {@link HostCreateSpec}.
 */
public class HostCreateSpecTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test
    public void testNotEmptyConstraint() {
      HostCreateSpec hostCreateSpec = new HostCreateSpec();

      ImmutableList<String> errors = validator.validate(hostCreateSpec);
      Assert.assertEquals(errors.size(), 4);
      Assert.assertEquals("address is invalid IP or Domain address (was null)", errors.get(0));
      Assert.assertEquals("password may not be null (was null)", errors.get(1));
      Assert.assertEquals("usageTags may not be null (was null)", errors.get(2));
      Assert.assertEquals("username may not be null (was null)", errors.get(3));
    }

    @Test
    public void testNotNullButEmptyUsageTags() {
      HostCreateSpec hostCreateSpec = new HostCreateSpec();

      hostCreateSpec.setUsername("username");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("1.1.1.1");
      hostCreateSpec.setUsageTags(new ArrayList<UsageTag>());

      ImmutableList<String> errors = validator.validate(hostCreateSpec);
      Assert.assertEquals(errors.size(), 1);
      Assert.assertEquals("usageTags size must be between 1 and 2147483647 (was [])", errors.get(0));

    }

    @Test
    public void testEmptyStringsForUsernameAndPassword() {
      HostCreateSpec hostCreateSpec = new HostCreateSpec();

      hostCreateSpec.setUsername("");
      hostCreateSpec.setPassword("");
      hostCreateSpec.setAddress("1.1.1.1");
      hostCreateSpec.setUsageTags(new ArrayList<UsageTag>() {{
        add(UsageTag.MGMT);
      }});

      ImmutableList<String> errors = validator.validate(hostCreateSpec);
      Assert.assertEquals(errors.size(), 2);
      Assert.assertEquals("password size must be between 1 and 2147483647 (was )", errors.get(0));
      Assert.assertEquals("username size must be between 1 and 2147483647 (was )", errors.get(1));
    }

    @Test
    public void testInvalidAddress() {
      HostCreateSpec hostCreateSpec = new HostCreateSpec();

      hostCreateSpec.setUsername("username");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAddress("fake");
      hostCreateSpec.setUsageTags(new ArrayList<UsageTag>() {{
        add(UsageTag.MGMT);
      }});

      ImmutableList<String> errors = validator.validate(hostCreateSpec);
      Assert.assertEquals(errors.size(), 1);
      assertThat(errors.get(0), startsWith("address fake is invalid IP or Domain Address"));
    }

    @Test
    public void testSuccessValidationForHostCreateSpec() {
      HostCreateSpec hostCreateSpec = new HostCreateSpec();

      hostCreateSpec.setUsername("username");
      hostCreateSpec.setPassword("password");
      hostCreateSpec.setAvailabilityZone("availabilityZone");
      hostCreateSpec.setAddress("1.1.1.1");
      hostCreateSpec.setUsageTags(new ArrayList<UsageTag>() {{
        add(UsageTag.MGMT);
      }});

      ImmutableList<String> errors = validator.validate(hostCreateSpec);
      Assert.assertEquals(errors.size(), 0);
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    HostCreateSpec hostCreateSpec;

    @BeforeMethod
    public void setup() {
      Map<String, String> hostMetadata = new HashMap<>();
      hostMetadata.put("id", "h_146_36_27");

      hostCreateSpec = new HostCreateSpec("10.146.1.1",
          "username",
          "password",
          "availabilityZone",
          new ArrayList<UsageTag>() {{
            add(UsageTag.MGMT);
          }},
          hostMetadata);
    }

    @Test
    public void testSerialization() throws Exception {
      String json = JsonHelpers.jsonFixture("fixtures/hostcreate.json");

      MatcherAssert.assertThat(JsonHelpers.fromJson(json, HostCreateSpec.class), is(hostCreateSpec));
      MatcherAssert.assertThat(JsonHelpers.asJson(hostCreateSpec), sameJSONAs(json).allowingAnyArrayOrdering());
    }
  }
}
