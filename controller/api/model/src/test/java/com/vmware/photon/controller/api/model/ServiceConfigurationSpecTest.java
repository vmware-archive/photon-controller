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

import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link ServiceConfigurationSpec}.
 */
public class ServiceConfigurationSpecTest {
  private ServiceConfigurationSpec serviceConfigurationSpec;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private ServiceConfigurationSpec createValidServiceConfigurationSpec() {
    ServiceConfigurationSpec spec = new ServiceConfigurationSpec();
    spec.setType(ServiceType.KUBERNETES);
    spec.setImageId("imageId");
    return spec;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      serviceConfigurationSpec = createValidServiceConfigurationSpec();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture("fixtures/service-configuration-spec.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(serviceConfigurationSpec), is(equalTo(json)));
      MatcherAssert.assertThat(
          JsonHelpers.fromJson(json, ServiceConfigurationSpec.class), is(serviceConfigurationSpec));
    }
  }

  /**
   * Tests for {@link ServiceConfigurationSpec#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      serviceConfigurationSpec = createValidServiceConfigurationSpec();
    }

    @Test
    public void testToString() {
      String expectedString = "ServiceConfigurationSpec{type=KUBERNETES, imageId=imageId}";
      assertThat(serviceConfigurationSpec.toString(), is(expectedString));
    }
  }
}
