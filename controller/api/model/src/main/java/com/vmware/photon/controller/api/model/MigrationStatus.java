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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Min;

import java.util.Objects;

/**
 * Migration status API representation.
 */
public class MigrationStatus {
  @JsonProperty
  @ApiModelProperty(value = "Completed data migration cycles.", required = true)
  @Min(0)
  private long completedDataMigrationCycles = 0;

  @JsonProperty
  @ApiModelProperty(value = "Progress of current migration cycle.", required = true)
  @Min(0)
  private long dataMigrationCycleProgress = 0;

  @JsonProperty
  @ApiModelProperty(value = "Size of the current migration cycle.", required = true)
  @Min(0)
  private long dataMigrationCycleSize = 0;

  @JsonProperty
  @ApiModelProperty(value = "Number of finished vib uploads.", required = true)
  @Min(0)
  private long vibsUploaded = 0;

  @JsonProperty
  @ApiModelProperty(value = "Number of putstanding vib uploads.", required = true)
  @Min(0)
  private long vibsUploading = 0;

  public long getVibsUploaded() {
    return vibsUploaded;
  }

  public void setVibsUploaded(long vibsUploaded) {
    this.vibsUploaded = vibsUploaded;
  }

  public long getVibsUploading() {
    return vibsUploading;
  }

  public void setVibsUploading(long vibsUploading) {
    this.vibsUploading = vibsUploading;
  }

  public long getCompletedDataMigrationCycles() {
    return completedDataMigrationCycles;
  }

  public void setCompletedDataMigrationCycles(long completedDataMigrationCycles) {
    this.completedDataMigrationCycles = completedDataMigrationCycles;
  }

  public long getDataMigrationCycleProgress() {
    return dataMigrationCycleProgress;
  }

  public void setDataMigrationCycleProgress(long dataMigrationCycleProgress) {
    this.dataMigrationCycleProgress = dataMigrationCycleProgress;
  }

  public long getDataMigrationCycleSize() {
    return dataMigrationCycleSize;
  }

  public void setDataMigrationCycleSize(long dataMigrationCycleSize) {
    this.dataMigrationCycleSize = dataMigrationCycleSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MigrationStatus other = (MigrationStatus) o;

    return super.equals(other)
        && Objects.equals(this.getCompletedDataMigrationCycles(), other.getCompletedDataMigrationCycles())
        && Objects.equals(this.getDataMigrationCycleProgress(), other.getDataMigrationCycleProgress())
        && Objects.deepEquals(this.getDataMigrationCycleSize(), other.getDataMigrationCycleSize())
        && Objects.equals(this.getVibsUploaded(), other.getVibsUploaded())
        && Objects.equals(this.getVibsUploading(), other.getVibsUploading());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getCompletedDataMigrationCycles(),
        this.getDataMigrationCycleProgress(),
        this.getDataMigrationCycleSize(),
        this.getVibsUploaded(),
        this.getVibsUploading());
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("completedDataMigrationCycles", this.getCompletedDataMigrationCycles())
        .add("dataMigrationCycleProgress", this.getDataMigrationCycleProgress())
        .add("dataMigrationCycleSize", this.getDataMigrationCycleSize())
        .add("vibsUploaded", this.getVibsUploaded())
        .add("vibsUploading", this.getVibsUploading());
  }
}
