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

import com.vmware.photon.controller.nsxclient.datatypes.L4ProtocolType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a L4PortSetNSService JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class L4PortSetNSService extends NSServiceElement {

  @JsonProperty(value = "l4_protocol", required = true)
  private L4ProtocolType protocolType;

  @JsonProperty(value = "source_ports", required = false)
  @Size(max = 15)
  private List<String> sourcePorts;

  @JsonProperty(value = "destination_ports", required = false)
  @Size(max = 15)
  private List<String> destinationPorts;

  public L4ProtocolType getProtocolType() {
    return this.protocolType;
  }

  public void setProtocolType(L4ProtocolType protocolType) {
    this.protocolType = protocolType;
  }

  public List<String> getSourcePorts() {
    return this.sourcePorts;
  }

  public void setSourcePorts(List<String> sourcePorts) {
    this.sourcePorts = sourcePorts;
  }

  public List<String> getDestinationPorts() {
    return this.destinationPorts;
  }

  public void setDestinationPorts(List<String> destinationPorts) {
    this.destinationPorts = destinationPorts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    L4PortSetNSService other = (L4PortSetNSService) o;
    return Objects.equals(getProtocolType(), other.getResourceType())
        && Objects.deepEquals(getSourcePorts(), other.getSourcePorts())
        && Objects.deepEquals(getDestinationPorts(), other.getDestinationPorts())
        && super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getProtocolType(),
        getSourcePorts(),
        getDestinationPorts());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
