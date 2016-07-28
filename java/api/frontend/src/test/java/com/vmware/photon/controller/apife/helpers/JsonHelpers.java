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

package com.vmware.photon.controller.apife.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.FixtureHelpers;

import java.io.IOException;

/**
 * This class implements helper routines for testing.
 *
 * These are mostly re-implementations of Dropwizard's JsonHelpers routines, which were deprecated sometime between
 * 0.6.2 and 0.8.2.
 */
public class JsonHelpers {

  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

  public static String asJson(Object o) throws JsonProcessingException {
    return MAPPER.writeValueAsString(o);
  }

  public static <T> T fromJson(String json, Class<T> type) throws IOException {
    return MAPPER.readValue(json, type);
  }

  public static String jsonFixture(String filename) throws IOException {
    return MAPPER.writeValueAsString(MAPPER.readValue(FixtureHelpers.fixture(filename), JsonNode.class));
  }
}
