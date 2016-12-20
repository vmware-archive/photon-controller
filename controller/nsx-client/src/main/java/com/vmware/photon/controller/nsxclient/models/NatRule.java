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

package com.vmware.photon.controller.nsxclient.models;

import com.vmware.photon.controller.nsxclient.datatypes.NatActionType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a NatRule JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class NatRule {

  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "action", required = true)
  private NatActionType natAction;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "enabled", required = false)
  private Boolean enabled;

  @JsonProperty(value = "logging", required = false)
  private Boolean loggingEnabled;

  @JsonProperty(value = "match_destination_network", required = false)
  private String matchDestinationNetwork;

  @JsonProperty(value = "match_service", required = false)
  private NSServiceElement matchService;

  @JsonProperty(value = "match_source_network", required = false)
  private String matchSourceNetwork;

  @JsonProperty(value = "rule_priority", required = false)
  public Integer rulePriority;

  @JsonProperty(value = "tags", required = false)
  public List<Tag> tags;

  @JsonProperty(value = "translated_network", required = false)
  public String translatedNetwork;

  @JsonProperty(value = "translated_ports", required = false)
  public String translatedPorts;

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public NatActionType getNatAction() {
    return this.natAction;
  }

  public void setNatAction(NatActionType natAction) {
    this.natAction = natAction;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Boolean getEnabled() {
    return this.enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getLoggingEnabled() {
    return this.loggingEnabled;
  }

  public void setLoggingEnabled(Boolean loggingEnabled) {
    this.loggingEnabled = loggingEnabled;
  }

  public String getMatchDestinationNetwork() {
    return this.matchDestinationNetwork;
  }

  public void setMatchDestinationNetwork(String matchDestinationNetwork) {
    this.matchDestinationNetwork = matchDestinationNetwork;
  }

  public NSServiceElement getMatchService() {
    return this.matchService;
  }

  public void setMatchService(NSServiceElement matchService) {
    this.matchService = matchService;
  }

  public String getMatchSourceNetwork() {
    return this.matchSourceNetwork;
  }

  public void setMatchSourceNetwork(String matchSourceNetwork) {
    this.matchSourceNetwork = matchSourceNetwork;
  }

  public Integer getRulePriority() {
    return this.rulePriority;
  }

  public void setRulePriority(Integer rulePriority) {
    this.rulePriority = rulePriority;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  public String getTranslatedNetwork() {
    return this.translatedNetwork;
  }

  public void setTranslatedNetwork(String translatedNetwork) {
    this.translatedNetwork = translatedNetwork;
  }

  public String getTranslatedPorts() {
    return this.translatedPorts;
  }

  public void setTranslatedPorts(String translatedPorts) {
    this.translatedPorts = translatedPorts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NatRule other = (NatRule) o;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getNatAction(), other.getNatAction())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.equals(getEnabled(), other.getEnabled())
        && Objects.equals(getLoggingEnabled(), other.getLoggingEnabled())
        && Objects.equals(getMatchDestinationNetwork(), other.getMatchDestinationNetwork())
        && Objects.equals(getMatchService(), other.getMatchService())
        && Objects.equals(getMatchSourceNetwork(), other.getMatchSourceNetwork())
        && Objects.equals(getRulePriority(), other.getRulePriority())
        && Objects.deepEquals(getTags(), other.getTags())
        && Objects.equals(getTranslatedNetwork(), other.getTranslatedNetwork())
        && Objects.equals(getTranslatedPorts(), other.getTranslatedPorts());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(),
        getNatAction(),
        getDisplayName(),
        getDescription(),
        getEnabled(),
        getLoggingEnabled(),
        getMatchDestinationNetwork(),
        getMatchService(),
        getMatchSourceNetwork(),
        getTags(),
        getTranslatedNetwork(),
        getTranslatedPorts());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
