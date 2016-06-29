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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;

import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines ContainersConfig.
 */
public class ContainersConfig {

  /**
   * List of known container types.
   */
  public enum ContainerType {
    Lightwave,
    LoadBalancer,
    ManagementUi,
    PhotonControllerCore,
  }

  /**
   * Defines container specification.
   */
  public static class Spec {

    private String serviceName;

    @NotNull
    private String type;

    @NotNull
    private Boolean isReplicated;

    @Range(min = 1)
    private int cpuCount;

    @Range(min = 4)
    private long memoryMb;

    @Range(min = 1)
    private int diskGb;

    @DefaultBoolean(value = false)
    private Boolean isPrivileged;

    @DefaultBoolean(value = true)
    private Boolean useHostNetwork;

    @NotNull
    private String containerImage;

    private String containerName;

    private Map<String, String> volumeBindings;

    private Map<Integer, Integer> portBindings;

    private String volumesFrom;

    private Map<String, String> dynamicParameters;

    public String getServiceName() {
      return serviceName;
    }

    public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Boolean getIsReplicated() {
      return isReplicated;
    }

    public void setIsReplicated(Boolean isReplicated) {
      this.isReplicated = isReplicated;
    }

    public int getCpuCount() {
      return cpuCount;
    }

    public void setCpuCount(int cpuCount) {
      this.cpuCount = cpuCount;
    }

    public long getMemoryMb() {
      return memoryMb;
    }

    public void setMemoryMb(int memoryMb) {
      this.memoryMb = memoryMb;
    }

    public int getDiskGb() {
      return diskGb;
    }

    public void setDiskGb(int diskGb) {
      this.diskGb = diskGb;
    }

    public Boolean getIsPrivileged() {
      return isPrivileged;
    }

    public void setIsPrivileged(Boolean isPrivileged) {
      this.isPrivileged = isPrivileged;
    }

    public Boolean getUseHostNetwork() {
      return useHostNetwork;
    }

    public void setUseHostNetwork(Boolean useHostNetwork) {
      this.useHostNetwork = useHostNetwork;
    }

    public String getContainerImage() {
      return containerImage;
    }

    public void setContainerImage(String containerImage) {
      this.containerImage = containerImage;
    }

    public String getContainerName() {
      return containerName;
    }

    public void setContainerName(String containerName) {
      this.containerName = containerName;
    }

    public Map<String, String> getVolumeBindings() {
      return volumeBindings;
    }

    public void setVolumeBindings(Map<String, String> volumeBindings) {
      this.volumeBindings = volumeBindings;
    }

    public Map<Integer, Integer> getPortBindings() {
      return portBindings;
    }

    public void setPortBindings(Map<Integer, Integer> portBindings) {
      this.portBindings = portBindings;
    }

    public String getVolumesFrom() {
      return volumesFrom;
    }

    public void setVolumesFrom(String volumesFrom) {
      this.volumesFrom = volumesFrom;
    }

    public Map<String, String> getDynamicParameters() {
      return dynamicParameters;
    }

    public void setDynamicParameters(Map<String, String> dynamicParameters) {
      this.dynamicParameters = dynamicParameters;
    }
  }

  @NotNull
  @NotEmpty
  private Map<String, Spec> containerSpecs = new HashMap<>();

  public Map<String, Spec> getContainerSpecs() {
    return containerSpecs;
  }

  public void setContainers(List<Spec> containers) {
    for (Spec c : containers) {
      containerSpecs.put(c.getType(), c);
    }
  }
}
