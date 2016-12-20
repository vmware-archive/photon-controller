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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Helper API error representation.
 */
@ApiModel(value = "This class captures a single error that occurred while processing an API request. " +
    "The ApiError structure is embedded in a Task and is also the entity returned on all 4xx class API errors. " +
    "In the 4xx case, ApiError is the entity returned along with the 4xx response code. The ApiError structure " +
    "also occurs within Task when that task completes with an error. E.g., a Vm creation that fails during " +
    "provisioning. In this case, the VM creation returns a 201 created indicating that it has successfully " +
    "accepted the request. When an error occurs after this stage, the error is captured into the task.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiError {

  @JsonProperty
  @ApiModelProperty(value = "This property contains a short, one-word textual representation of the error. E.g. " +
      "QuotaError, DiskNotFound, InvalidJson, TooManyRequests, etc.",
      required = true)
  private String code;

  @JsonProperty
  @ApiModelProperty(value = "This property provides a terse description of the error.", required = true)
  private String message;

  @JsonProperty
  @ApiModelProperty(value = "This property provides an optional string-string map of additional data that " +
      "provides additional detail and data. Each error has a unique set of additional meta-data.",
      required = true)
  private Map<String, String> data;

  public ApiError() {
  }

  public ApiError(String code, String message, Map<String, String> data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, String> getData() {
    return data;
  }

  public void setData(Map<String, String> data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ApiError other = (ApiError) o;

    return Objects.equals(this.code, other.code) &&
        Objects.equals(this.message, other.message) &&
        Objects.equals(this.data, other.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, data);
  }
}
