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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneDcpBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.FlavorSqlBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;

import com.google.common.base.Optional;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;

/**
 * Tests {@link TaskFeClient}.
 */
public class TaskFeClientTest {

  TaskFeClient feClient;

  /**
   * dummy test to keep IntelliJ happy.
   */
  @Test
  public void dummy() {
  }

  /**
   * Tests the find method.
   */
  public class FindTests {
    TaskBackend taskBackend;

    @BeforeMethod
    public void setUp() {
      taskBackend = mock(TaskBackend.class);
      feClient = new TaskFeClient(
          taskBackend, mock(TenantBackend.class), mock(ProjectBackend.class),
          mock(ResourceTicketBackend.class), mock(VmBackend.class), mock(DiskBackend.class),
          mock(ImageBackend.class), mock(FlavorSqlBackend.class), mock(HostBackend.class),
          mock(AvailabilityZoneDcpBackend.class));
    }

    /**
     * Tests that taskBackend is invoked with correct params.
     */
    @Test
    public void testTaskBackendIsCalled() throws Throwable {
      Optional id = Optional.of("id");
      Optional kind = Optional.of("kind");
      Optional state = Optional.of("state");

      ResourceList result = feClient.find(id, kind, state);
      assertThat(result, notNullValue());
      assertThat(result.getItems().size(), is(0));

      verify(taskBackend).filter(id, kind, state);
    }
  }

}
