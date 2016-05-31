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

package com.vmware.photon.controller.cloudstore.dcp;

import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.StatusService;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

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
   * Tests {@link StatusService#handleGet(com.vmware.xenon.common.Operation)}.
   */
  public class HandleGetTest {

    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUp() throws Throwable {
      testEnvironment = TestEnvironment.create(1);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      testEnvironment.stop();
      testEnvironment = null;
    }

    @Test
    public void testReady() throws Throwable {
      Status status = testEnvironment.getServiceState(StatusService.SELF_LINK, Status.class);
      assertThat(status.getType(), is(StatusType.READY));
    }

    @Test
    public void testInitializing() throws Throwable {
      PhotonControllerXenonHost host = testEnvironment.getHosts()[0];
      Operation delete = Operation
          .createDelete(UriUtils.buildUri(host, FlavorServiceFactory.SELF_LINK))
          .setBody("{}");

      ServiceHostUtils.sendRequestAndWait(host, delete, "test");

      Status status = testEnvironment.getServiceState(StatusService.SELF_LINK, Status.class);
      assertThat(status.getType(), is(StatusType.INITIALIZING));
      assertThat(status.getBuild_info(), is("build-info: N/A"));
    }
  }
}
