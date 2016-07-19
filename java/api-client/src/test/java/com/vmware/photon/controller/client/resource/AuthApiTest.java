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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.model.Auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for {@link AuthApi}.
 */
public class AuthApiTest extends ApiTestBase {

  @Test
  public void testGetAuthStatus() throws IOException {
    final Auth auth = new Auth();

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(auth);

    setupMocks(serialized, HttpStatus.SC_OK);

    AuthApi authApi = new AuthApi(restClient);

    Auth response = authApi.getAuthStatus();
    assertEquals(response, auth);
  }

  @Test
  public void testGetAuthStatusAsync() throws IOException, InterruptedException {
    final Auth auth = new Auth();

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(auth);

    setupMocks(serialized, HttpStatus.SC_OK);

    AuthApi authApi = new AuthApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    authApi.getAuthStatusAsync(new FutureCallback<Auth>() {
      @Override
      public void onSuccess(@Nullable Auth result) {
        assertEquals(result, auth);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });
  }
}
