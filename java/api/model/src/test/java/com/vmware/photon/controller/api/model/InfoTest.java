/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import java.io.IOException;


/**
 * Tests {@link Info}.
 */
public class InfoTest {

  private static final String JSON_FILE = "fixtures/info.json";

  private static Info sampleInfo = new Info();
  {
    sampleInfo.setNetworkType(NetworkType.SOFTWARE_DEFINED);
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {
    @Test
    public void testValidateInfo() {
      ImmutableList<String> violations = new Validator().validate(sampleInfo);
      assertThat(violations.isEmpty(), is(true));
    }
  }

  /**
   * Tests {@link Info#toString()}.
   */
  public class ToStringTest {
    @Test
    public void testCorrectString() {
      String expectedString = "Info{networkType=SOFTWARE_DEFINED}";
      assertThat(sampleInfo.toString(), is(expectedString));
    }
  }

  /**
   * Tests for serialization.
   */
  public class SerializationTest {
    @Test
    public void testInfo() throws IOException {
      String json = jsonFixture(JSON_FILE);
      assertThat(asJson(sampleInfo), is(json));
      assertThat(fromJson(json, Info.class), is(sampleInfo));
    }
  }
}
