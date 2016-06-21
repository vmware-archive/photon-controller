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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.common.xenon.host.StatusService;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.xenon.common.Operation;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.spy;


/**
 * Tests {@link com.vmware.photon.controller.common.xenon.host.StatusService}.
 */
public class StatusServiceTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests Scheduler {@link StatusService#handleGet(com.vmware.xenon.common.Operation)}.
   */
  public class HandleGetTest {
    StatusService service;

    private TestHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new StatusService());
      host = TestHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    @Test
    public void testReady() throws Throwable {
      Operation startOp = host.startServiceSynchronously(service, null);
      assertThat(startOp.getStatusCode(), is(200));
    }
  }
}
