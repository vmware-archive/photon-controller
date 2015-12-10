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

package com.vmware.photon.controller.apife.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;

/**
 * Image configuration.
 */
public class ImageConfig {

  private static final String DEFAULT_LOCAL_STORE = "/tmp/images";

  /**
   * Default timeout in seconds to wait for image replication to complete.
   * (This time is in seconds.)
   */
  private static final Duration DEFAULT_IMAGE_REPLICATION_TIMEOUT = Duration.seconds(60 * 60);

  private Duration replicationTimeout = DEFAULT_IMAGE_REPLICATION_TIMEOUT;

  @JsonProperty("use_esx_store")
  private boolean useEsxStore = false;

  private String endpoint;

  private String datastore;

  public ImageConfig() {
    replicationTimeout = DEFAULT_IMAGE_REPLICATION_TIMEOUT;
  }

  public Duration getReplicationTimeout() {
    return replicationTimeout;
  }

  public boolean useEsxStore() {
    return useEsxStore;
  }

  public void setUseEsxStore(boolean flag) {
    this.useEsxStore = flag;
  }

  public String getLocalStore() {
    return DEFAULT_LOCAL_STORE;
  }

  public String getEndpoint() {
    return checkNotNull(endpoint);
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getEndpointHostAddress() {
    URI hostUri = URI.create(this.getEndpoint());
    return hostUri.getHost();
  }

  public String getDatastore() {
    return checkNotNull(datastore);
  }

  public void setDatastore(String datastore) {
    this.datastore = datastore;
  }
}
