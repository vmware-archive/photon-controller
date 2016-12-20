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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.model.UsageTag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Ordering;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper methods for UsageTag related operations.
 */
public class UsageTagHelper {

  private static final Logger logger = LoggerFactory.getLogger(UsageTagHelper.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static List<UsageTag> deserialize(String concatenatedUsageTags) {
    if (StringUtils.isBlank(concatenatedUsageTags)) {
      throw new IllegalArgumentException("Blank string cannot be deserialized to list of UsageTags");
    }

    try {
      List<UsageTag> usageTags = objectMapper.readValue(concatenatedUsageTags,
          new TypeReference<List<UsageTag>>() {
          });
      Collections.sort(usageTags);
      return usageTags;
    } catch (IOException e) {
      logger.error(
          String.format("Error deserializing UsageTag %s", concatenatedUsageTags), e);
      throw new IllegalArgumentException(
          String.format("Error deserializing UsageTag %s", concatenatedUsageTags), e);
    }
  }

  public static Set<String> deserializeToStringSet(String concatenatedUsageTags) {
    Set<String> usageTags = new HashSet<>();
    for (UsageTag usageTag : deserialize(concatenatedUsageTags)) {
      usageTags.add(usageTag.toString());
    }
    return usageTags;
  }

  public static String serialize(List<UsageTag> usageTags) {
    if (usageTags == null) {
      throw new IllegalArgumentException("Null usage tag list cannot be serialized");
    }

    try {
      return objectMapper.writeValueAsString(Ordering.usingToString().natural().immutableSortedCopy(usageTags));
    } catch (JsonProcessingException e) {
      logger.error("Error serializing usageTags list", e);
      throw new IllegalArgumentException(String.format("Error serializing usageTags list: %s",
          e.getMessage()));
    }
  }

  public static String serialize(Set<String> usageTags) {
    if (usageTags == null) {
      throw new IllegalArgumentException("Null usage tag set cannot be serialized");
    }

    try {
      return objectMapper.writeValueAsString(Ordering.usingToString().natural().immutableSortedCopy(usageTags));
    } catch (JsonProcessingException e) {
      logger.error("Error serializing usageTags set", e);
      throw new IllegalArgumentException(String.format("Error serializing usageTags set: %s",
          e.getMessage()));
    }
  }
}
