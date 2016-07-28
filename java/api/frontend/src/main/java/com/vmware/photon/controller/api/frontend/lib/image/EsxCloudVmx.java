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

package com.vmware.photon.controller.api.frontend.lib.image;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EsxCloudVmx configuration data.
 */
public class EsxCloudVmx {
  public final Map<String, String> configuration;
  public final List<Property> parameters;

  public EsxCloudVmx() {
    configuration = new LinkedHashMap<>();
    parameters = new ArrayList<>();
  }

  public static Map<String, String> toImageSettings(EsxCloudVmx ecv) {
    Map<String, String> imageSettings = new HashMap<>();

    // configuration does not have null value
    imageSettings.putAll(ecv.configuration);
    for (EsxCloudVmx.Property property : ecv.parameters) {
      imageSettings.put(property.name, null);
    }

    return imageSettings;
  }

  public static EsxCloudVmx fromImageSettings(Map<String, String> imageSettings) {
    EsxCloudVmx ecv = new EsxCloudVmx();
    for (Map.Entry<String, String> imageSetting : imageSettings.entrySet()) {
      if (imageSetting.getValue() == null) {
        ecv.parameters.add(new EsxCloudVmx.Property(imageSetting.getKey()));
      } else {
        ecv.configuration.put(imageSetting.getKey(), imageSetting.getValue());
      }
    }

    return ecv;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    EsxCloudVmx other = (EsxCloudVmx) o;

    return Objects.equals(configuration, other.configuration) &&
        Objects.equals(parameters, other.parameters);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(configuration, parameters);
  }

  /**
   * EsxCloudVmx property.
   */
  public static class Property {
    public String name;

    public Property(@JsonProperty("name") String name) {
      this.name = name;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Property other = (Property) o;

      return Objects.equals(name, other.name);
    }
  }
}
