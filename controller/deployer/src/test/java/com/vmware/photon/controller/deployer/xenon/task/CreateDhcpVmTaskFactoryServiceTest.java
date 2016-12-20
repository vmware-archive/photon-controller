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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.xenon.common.Service;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.util.EnumSet;

/**
 * This class implements tests for the {@link CreateDhcpVmTaskFactoryService} class.
 */
public class CreateDhcpVmTaskFactoryServiceTest {

  private CreateDhcpVmTaskFactoryService createDhcpVmTaskFactoryService;

  @BeforeClass
  public void setUpClass() {
    createDhcpVmTaskFactoryService = new CreateDhcpVmTaskFactoryService();
  }

  @Test
  public void testCapabilityInitialization() {

    EnumSet<Service.ServiceOption> expected = EnumSet.of(
        Service.ServiceOption.CONCURRENT_GET_HANDLING,
        Service.ServiceOption.CONCURRENT_UPDATE_HANDLING,
        Service.ServiceOption.FACTORY);

    assertThat(createDhcpVmTaskFactoryService.getOptions(), is(expected));
  }

  @Test
  public void testCreateServiceInstance() throws Throwable {
    Service service = createDhcpVmTaskFactoryService.createServiceInstance();
    assertThat(service, instanceOf(CreateDhcpVmTaskService.class));
  }
}
