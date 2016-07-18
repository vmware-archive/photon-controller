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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.api.client.ApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Health check for http based components such as API-FE and LB.
 */
public class HttpBasedHealthChecker implements HealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(HttpBasedHealthChecker.class);

  private final ApiClient apiClient;

  public HttpBasedHealthChecker(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  @Override
  public boolean isReady() {
    try {
      // Check if the service is up. Using auth api since it is not locked.
      apiClient.getAuthApi().getAuthStatus();
      return true;
    } catch (Exception e) {
      logger.error("Get Auth Status failed due to: " + e);
      return false;
    }
  }
}
