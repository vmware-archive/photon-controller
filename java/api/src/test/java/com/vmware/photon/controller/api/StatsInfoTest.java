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
 * Tests {@link StatsInfo}.
 */
public class StatsInfoTest {

  private static final String JSON_FILE = "fixtures/statsinfo.json";

  private static StatsInfo createStatsInfo() {
    StatsInfo stats = new StatsInfo();
    stats.setEnabled(true);
    stats.setStoreEndpoint("10.146.64.236");
    stats.setPort(2004);

    return stats;
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
    public void testValidStatsInfo() {
      ImmutableList<String> violations = validator.validate(createStatsInfo());
      assertThat(violations.isEmpty(), is(true));
    }

  }

  /**
   * Tests {@link StatsInfo#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "StatsInfo{enabled=true, storeEndpoint=10.146.64.236, port=2004}";
      StatsInfo statsInfo = createStatsInfo();
      assertThat(statsInfo.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testStatsInfo() throws IOException {
      StatsInfo statsInfo = createStatsInfo();
      String json = jsonFixture(JSON_FILE);

      assertThat(asJson(statsInfo), is(equalTo(json)));
      assertThat(fromJson(json, StatsInfo.class), is(statsInfo));
    }
  }
}
