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

package com.vmware.photon.controller.common.thrift;

import java.util.concurrent.TimeUnit;

/**
 * Client pool options (timeouts, limits).
 */
public class ClientPoolOptions {

  private int maxClients = 1;
  private int maxWaiters = 1;
  private long timeoutMs = 0;
  private String serviceName;

  public ClientPoolOptions() {
  }

  public ClientPoolOptions(ClientPoolOptions other) {
    this.maxClients = other.maxClients;
    this.maxWaiters = other.maxWaiters;
    this.timeoutMs = other.timeoutMs;
    this.serviceName = other.serviceName;
  }

  public int getMaxClients() {
    return maxClients;
  }

  public ClientPoolOptions setMaxClients(int maxClients) {
    this.maxClients = maxClients;
    return this;
  }

  public int getMaxWaiters() {
    return maxWaiters;
  }

  public ClientPoolOptions setMaxWaiters(int maxWaiters) {
    this.maxWaiters = maxWaiters;
    return this;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public ClientPoolOptions setTimeout(long duration, TimeUnit timeUnit) {
    this.timeoutMs = timeUnit.toMillis(duration);
    return this;
  }

  public String getServiceName() {
    return serviceName;
  }

  public ClientPoolOptions setServiceName(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }
}
