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

import com.vmware.photon.controller.api.model.SystemStatus;
import com.vmware.photon.controller.status.gen.StatusType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.fail;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implements tests for {@link SystemStatus}.
 */
public class SystemStatusApiTest extends ApiTestBase {

  @Test
  public void testGetSystemStatus() throws IOException {
    SystemStatus systemStatus = new SystemStatus();
    systemStatus.setStatus(StatusType.READY);

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(systemStatus);

    setupMocks(serialized, HttpStatus.SC_OK);

    SystemStatusApi systemStatusApi = new SystemStatusApi(restClient);

    SystemStatus response = systemStatusApi.getSystemStatus();
    assertEquals(response, systemStatus);
  }

  @Test
  public void testGetVmAsync() throws IOException, InterruptedException {
    final SystemStatus systemStatus = new SystemStatus();
    systemStatus.setStatus(StatusType.READY);

    ObjectMapper mapper = new ObjectMapper();
    String serialized = mapper.writeValueAsString(systemStatus);

    setupMocks(serialized, HttpStatus.SC_OK);

    SystemStatusApi systemStatusApi = new SystemStatusApi(restClient);

    final CountDownLatch latch = new CountDownLatch(1);

    systemStatusApi.getSystemStatusAsync(new FutureCallback<SystemStatus>() {
      @Override
      public void onSuccess(@Nullable SystemStatus result) {
        assertEquals(result, systemStatus);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        fail(t.toString());
        latch.countDown();
      }
    });

    assertThat(latch.await(COUNTDOWNLATCH_AWAIT_TIMEOUT, TimeUnit.SECONDS), is(true));
  }
}
