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

import com.vmware.photon.controller.api.model.builders.StatsInfoBuilder;
import com.vmware.photon.controller.api.model.constraints.StatsDisabled;
import com.vmware.photon.controller.api.model.constraints.StatsEnabled;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests {@link StatsInfo}.
 */
public class StatsInfoTest {

  private static final String JSON_FILE = "fixtures/statsinfo.json";

  private static StatsInfo createStatsInfo() {
    StatsInfo stats = new StatsInfo();
    stats.setEnabled(true);
    stats.setStoreEndpoint("10.146.64.236");
    stats.setStorePort(2004);
    stats.setStoreType(StatsStoreType.GRAPHITE);

    return stats;
  }

  @Test
  private void dummy() {
  }


  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private final String[] statsEnabledErrorMsgs = new String[]{
        "storeEndpoint may not be null (was null)",
        "storePort may not be null (was null)"
    };

    private final String[] statsDisabledErrorMsgs = new String[]{
        "storeEndpoint must be null (was e)",
        "storePort must be null (was 1)",
    };

    private Validator validator = new Validator();

    @Test(dataProvider = "validStatsInfo")
    public void testValidStatsInfo(StatsInfo stats) {
      ImmutableList<String> violations = validator.validate(stats);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "validStatsInfo")
    public Object[][] getValidStatsInfo() {
      return new Object[][]{
          {
            new StatsInfoBuilder()
              .enabled(false)
              .build(),
          },
          {
            new StatsInfoBuilder()
              .enabled(true).storeEndpoint("test").storePort(100).storeType(StatsStoreType.GRAPHITE)
              .build(),
          },
          {
            new StatsInfoBuilder()
              .enabled(true).storeEndpoint("test")
              .build()
          },
      };
    }

    @Test(dataProvider = "invalidStatsInfo")
    public void testInvalidStatsInfo(StatsInfo stats, String[] errorMsgs) {
      ImmutableList<String> violations;
      if (stats.getEnabled()) {
        violations = validator.validate(stats, StatsEnabled.class);
      } else {
        violations = validator.validate(stats, StatsDisabled.class);
      }

      assertThat(violations.size(), is(errorMsgs.length));
      assertThat(CollectionUtils.isEqualCollection(violations, Arrays.asList(errorMsgs)), is(true));
    }

    @DataProvider(name = "invalidStatsInfo")
    public Object[][] getInvalidStatsInfo() {
      return new Object[][]{
          {new StatsInfoBuilder()
              .enabled(true)
              .build(),
              statsEnabledErrorMsgs},
          {new StatsInfoBuilder()
              .enabled(false)
              .storeEndpoint("e")
              .storePort(1)
              .storeType(StatsStoreType.GRAPHITE)
              .build(),
              statsDisabledErrorMsgs},
      };
    }
  }

  /**
   * Tests {@link StatsInfo#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "StatsInfo{enabled=true, storeEndpoint=10.146.64.236, storePort=2004, storeType=GRAPHITE}";
      StatsInfo stats = createStatsInfo();
      assertThat(stats.toString(), is(expectedString));
    }
  }

  /**
   * Tests serialization.
   */
  public class SerializationTest {

    @Test
    public void testStatsInfo() throws IOException {
      StatsInfo stats = createStatsInfo();
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(stats), is(equalTo(json)));
      assertThat(JsonHelpers.fromJson(json, StatsInfo.class), is(stats));
    }
  }
}
