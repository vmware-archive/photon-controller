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
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test {@link Host}.
 */
public class HostTest {
  private static final String JSON_FILE = "fixtures/host.json";

  private Host host;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    @BeforeMethod
    public void setup() {

      Map<String, String> hostMetadata = new HashMap<>();
      hostMetadata.put("id", "h_146_36_27");

      host = new Host("10.146.1.1",
          "username",
          "password",
          "availabilityZone",
          "6.0",
          new ArrayList<UsageTag>() {{
            add(UsageTag.MGMT);
          }},
          hostMetadata);

      host.setId("e897bfb1-0f25-4fb8-985d-e3d0a337fe7a");
      host.setState(HostState.CREATING);

      List<HostDatastore> hostDatastoreList = new ArrayList<>();
      HostDatastore ds = new HostDatastore();
      ds.setDatastoreId("id");
      ds.setMountPoint("ds1");
      ds.setImageDatastore(true);
      hostDatastoreList.add(ds);
      host.setDatastores(hostDatastoreList);
    }

    @Test
    public void testSerialization() throws Exception {
      MatcherAssert.assertThat(
          JsonHelpers.asJson(host), sameJSONAs(JsonHelpers.jsonFixture(JSON_FILE)).allowingAnyArrayOrdering());
    }

    @Test
    public void testDeserialization() throws Exception {
      // clear fields that we ignore from serialization/deserialization
      host.setDatastores(null);

      MatcherAssert.assertThat(JsonHelpers.fromJson(JsonHelpers.jsonFixture(JSON_FILE), Host.class), is(host));
    }
  }
}
