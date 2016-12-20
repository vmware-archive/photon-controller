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

public class SwaggerModelProperty {
  @JsonProperty
  private String type;

  @JsonProperty
  private boolean required;

  @JsonProperty
  private HashMap<String, String> items;

  @JsonProperty
  private String description;

  @JsonProperty
  private HashMap<String, Object> allowableValues;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public HashMap<String, String> getItems() {
    return items;
  }

  public void setItems(HashMap<String, String> items) {
    this.items = items;
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
}
