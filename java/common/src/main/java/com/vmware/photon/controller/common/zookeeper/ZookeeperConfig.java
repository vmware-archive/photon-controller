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

package com.vmware.photon.controller.common.zookeeper;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Zookeeper configuration. Every component that needs to connect to ZK needs an instance of this.
 */
@SuppressWarnings("UnusedDeclaration")
public class ZookeeperConfig {

  @NotNull
  @NotEmpty
  @JsonProperty("quorum")
  private String quorum;

  @Valid
  @NotNull
  private RetryConfig retries = new RetryConfig();

  private String namespace;

  private String hostMonitorBackend = "zookeeper";

  public String getQuorum() {
    return quorum;
  }

  public void setQuorum(String quorum) {
    this.quorum = quorum;
  }

  public RetryConfig getRetries() {
    return retries;
  }

  public void setRetries(RetryConfig retries) {
    this.retries = retries;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getHostMonitorBackend() {
    return hostMonitorBackend;
  }

  public void setHostMonitorBackend(String backend) {
    this.hostMonitorBackend = backend;
  }

  /**
   * ZooKeeper operation retry config.
   */
  public static class RetryConfig {

    @Min(1)
    @JsonProperty("base_sleep_ms")
    private int baseSleepTimeMs = 5;

    @Min(1)
    @JsonProperty("max_sleep_ms")
    private int maxSleepTimeMs = 500;

    @Min(0)
    @JsonProperty("max_retries")
    private int maxRetries = 10;

    public int getBaseSleepTimeMs() {
      return baseSleepTimeMs;
    }

    public void setBaseSleepTimeMs(int baseSleepTimeMs) {
      this.baseSleepTimeMs = baseSleepTimeMs;
    }

    public int getMaxSleepTimeMs() {
      return maxSleepTimeMs;
    }

    public void setMaxSleepTimeMs(int maxSleepTimeMs) {
      this.maxSleepTimeMs = maxSleepTimeMs;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }
  }
}
