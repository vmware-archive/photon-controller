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

import java.util.List;

public class SwaggerOperation {
  @JsonProperty
  private String httpMethod;

  @JsonProperty
  private String summary;

  @JsonProperty
  private String notes;

  @JsonProperty
  private String responseClass;

  @JsonProperty
  private String nickname;

  @JsonProperty
  private List<SwaggerParameter> parameters;

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getResponseClass() {
    return responseClass;
  }

  public void setResponseClass(String responseClass) {
    this.responseClass = responseClass;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public List<SwaggerParameter> getParameters() {
    return parameters;
  }

  public void setParameters(List<SwaggerParameter> parameters) {
    this.parameters = parameters;
  }
}
