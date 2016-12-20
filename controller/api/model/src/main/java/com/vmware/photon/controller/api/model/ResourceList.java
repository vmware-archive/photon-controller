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

import java.util.List;

/**
 * Resource list API representation.
 * <p/>
 * The ResourceList is a generic list type for all resource collections at the API layer.
 * This representation is returned when a findById-by-name, findById-by-tag,
 * or findById-all selection is made against one of the collections e.g., /tenants,
 * /tenants/projects, /projects/{id}/vms is issued.
 *
 * @param <T> resource type
 */
@ApiModel(value = "This class represents a type collection of objects. The by-name, by-tag, and unfiltered " +
    "enumeration entry points (e.g., /v1/project/{id}/vms) all return a collection of entities wrapped in this. " +
    "When pagination is implemented, pagination related properties will live in this class.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceList<T> {
  public static final String CLASS_NAME = "ResourceList";

  private List<T> items;
  private String nextPageLink;
  private String previousPageLink;

  public ResourceList() {
  }

  public ResourceList(List<T> items) {
    this(items, null, null);
  }

  public ResourceList(@JsonProperty("items") List<T> items,
                      @JsonProperty("nextPageLink") String nextPageLink,
                      @JsonProperty("previousPageLink") String previousPageLink) {
    this.items = items;
    this.nextPageLink = nextPageLink;
    this.previousPageLink = previousPageLink;
  }

  public List<T> getItems() {
    return items;
  }

  public void setItems(List<T> items) {
    this.items = items;
  }

  public String getNextPageLink() {
    return nextPageLink;
  }

  public void setNextPageLink(String nextPageLink) {
    this.nextPageLink = nextPageLink;
  }

  public String getPreviousPageLink() {
    return previousPageLink;
  }

  public void setPreviousPageLink(String previousPageLink) {
    this.previousPageLink = previousPageLink;
  }
}
