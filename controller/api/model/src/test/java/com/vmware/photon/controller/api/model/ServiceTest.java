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

import com.google.common.collect.ImmutableMap;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

/**
 * Tests {@link Service}.
 */
public class ServiceTest {
  private Validator validator = new Validator();
  private Service service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private Service createValidService() {
    Service c = new Service();
    c.setId("id");
    c.setType(ServiceType.KUBERNETES);
    c.setState(ServiceState.READY);
    c.setName("name");
    c.setProjectId("projectId");
    c.setWorkerCount(3);
    c.setMasterVmFlavorName("flavor");
    c.setOtherVmFlavorName("flavor");
    c.setImageId("image");
    c.setExtendedProperties(ImmutableMap.of("containerNetwork", "10.1.0.0/16"));
    return c;
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {
    @BeforeMethod
    public void setUp() {
      service = createValidService();
    }

    @Test
    public void testSerialization() throws IOException {
      String json = JsonHelpers.jsonFixture("fixtures/service.json");
      MatcherAssert.assertThat(JsonHelpers.asJson(service), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, Service.class), is(service));
    }
  }

  /**
   * Tests for {@link Service#toString()}.
   */
  public class ToStringTest {
    @BeforeMethod
    public void setUp() {
      service = createValidService();
    }

    @Test
    public void testToString() {
      String expectedString = "Service{id=id, Kind=service, name=name, type=KUBERNETES, state=READY," +
          " projectId=projectId, workerCount=3, masterVmFlavorName=flavor, otherVmFlavorName=flavor, " +
          "imageId=image, errorReason=null, extendedProperties={containerNetwork=10.1.0.0/16}}";
      assertThat(service.toString(), is(expectedString));
    }
  }
}
