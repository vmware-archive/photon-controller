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

import com.vmware.photon.controller.api.model.base.Infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.persistence.ElementCollection;
import javax.validation.Valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * VM API representation.
 */
@ApiModel(value = "This class is the full fidelity representation of a VM object.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Vm extends Infrastructure {

  public static final String KIND = "vm";
  @JsonProperty
  @ApiModelProperty(value = "kind=\"vm\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the VM",
      allowableValues = "CREATING,STARTED,SUSPENDED,STOPPED,ERROR,DELETED",
      required = true)
  private VmState state;
  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Supplies this list of disks attached to the VM. During VM creation, this list contains " +
      "the list of disks that should be created and attached to the VM. Persistent disks can be attached to the VM " +
      "only after the VM has been created.",
      required = true)
  private List<AttachedDisk> attachedDisks = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty(value = "The id of the source image to create VM from.")
  private String sourceImageId;

  @JsonProperty
  @ApiModelProperty(value = "The host the vm is located on.")
  private String host;

  @JsonProperty
  @ApiModelProperty(value = "The datastore the vm is located on.")
  private String datastore;

  @JsonProperty
  @ApiModelProperty(value = "The ISO list attached to the vm")
  private List<Iso> attachedIsos = new ArrayList<>();

  @ElementCollection
  @JsonProperty
  @ApiModelProperty(value = "Custom metadata for the VM instance")
  private Map<String, String> metadata = new HashMap<String, String>();

  @JsonIgnore
  private String projectId;

  @Override
  public String getKind() {
    return kind;
  }

  public String getSourceImageId() {
    return this.sourceImageId;
  }

  public void setSourceImageId(String sourceImageId) {
    this.sourceImageId = sourceImageId;
  }

  public String getHost() {
    return this.host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getDatastore() {
    return datastore;
  }

  public void setDatastore(String datastore) {
    this.datastore = datastore;
  }

  public VmState getState() {
    return state;
  }

  public void setState(VmState state) {
    this.state = state;
  }

  public List<AttachedDisk> getAttachedDisks() {
    return attachedDisks;
  }

  public void setAttachedDisks(List<AttachedDisk> attachedDisks) {
    this.attachedDisks = attachedDisks;
  }

  public List<Iso> getAttachedIsos() {
    return attachedIsos;
  }

  public void addAttachedIso(Iso iso) {
    this.attachedIsos.add(iso);
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public String getProjectId() {
    return this.projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Vm other = (Vm) o;

    return super.equals(other) &&
        Objects.equals(state, other.state) &&
        Objects.equals(sourceImageId,
            other.sourceImageId) &&
        Objects.equals(host, other.host) &&
        Objects.equals(datastore, other.datastore) &&
        Objects.equals(attachedDisks, other.attachedDisks) &&
        Objects.equals(attachedIsos, other.attachedIsos) &&
        Objects.equals(metadata, other.metadata) &&
        Objects.equals(projectId, other.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), state, sourceImageId, host, datastore, attachedDisks, attachedIsos, metadata, projectId);
  }
}
