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

package com.vmware.photon.controller.rootscheduler.xenon;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.helpers.xenon.SchedulerTestEnvironment;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * Tests {@link StatusService}.
 */
public class StatusServiceTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests Scheduler {@link StatusService#handleGet}.
   */
  public class HandleGetTest {
    @Mock
    private RootSchedulerConfig config;

    @Mock
    private HostClient client;

    @Mock
    private ConstraintChecker checker;

    @Mock
    private XenonRestClient xenonRestClient;

    @Mock
    private HostClientFactory hostClientFactory;

    @Mock
    private CloudStoreHelper cloudStoreHelper;

    private SchedulerTestEnvironment testEnvironment;

    @BeforeMethod
    public void setUp() throws Throwable {
      MockitoAnnotations.initMocks(this);
      testEnvironment = SchedulerTestEnvironment.create(
          hostClientFactory, config, checker, cloudStoreHelper, 1);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test
    public void testReady() throws Throwable {
      Status status = testEnvironment.getServiceState(StatusService.SELF_LINK, Status.class);
      assertThat(status.getType(), is(StatusType.READY));
    }
  }
}
