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

import com.vmware.photon.controller.api.model.Auth;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.AuthApi;

import org.testng.annotations.Test;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link HttpBasedHealthChecker}.
 */
public class HttpBasedHealthCheckerTest {

  @Test
  public void testSuccess() throws Throwable {
    ApiClient apiClient = mock(ApiClient.class);
    AuthApi authApi = mock(AuthApi.class);

    doReturn(authApi).when(apiClient).getAuthApi();
    doReturn(new Auth()).when(authApi).getAuthStatus();

    HealthChecker healthChecker = new HttpBasedHealthChecker(apiClient);
    boolean response = healthChecker.isReady();

    assertTrue(response);
  }

  @Test
  public void testFailure() throws Throwable {
    ApiClient apiClient = mock(ApiClient.class);
    AuthApi authApi = mock(AuthApi.class);

    doReturn(authApi).when(apiClient).getAuthApi();
    doThrow(new RuntimeException("Failed to get auth status")).when(authApi).getAuthStatus();

    HealthChecker healthChecker = new HttpBasedHealthChecker(apiClient);
    boolean response = healthChecker.isReady();

    assertFalse(response);
  }
}
