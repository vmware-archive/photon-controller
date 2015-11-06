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

package com.vmware.photon.controller.swagger.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

public class SwaggerParameter {
  @JsonProperty
  private String name;

  @JsonProperty
  private boolean required;

  @JsonProperty
  private boolean allowMultiple;

  @JsonProperty
  private String dataType;

  @JsonProperty
  private String paramType;

  @JsonProperty
  private String description;

  @JsonProperty
  private HashMap<String, Object> allowableValues;

  @JsonProperty
  private String defaultValue;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public boolean isAllowMultiple() {
    return allowMultiple;
  }

  public void setAllowMultiple(boolean allowMultiple) {
    this.allowMultiple = allowMultiple;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getParamType() {
    return paramType;
  }

  public void setParamType(String paramType) {
    this.paramType = paramType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public HashMap<String, Object> getAllowableValues() {
    return allowableValues;
  }

  public void setAllowableValues(HashMap<String, Object> allowableValues) {
    this.allowableValues = allowableValues;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }
}
