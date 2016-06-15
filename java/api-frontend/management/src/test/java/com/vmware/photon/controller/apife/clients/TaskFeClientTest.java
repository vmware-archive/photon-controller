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

import com.vmware.photon.controller.api.AvailabilityZone;
import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneXenonBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.ArrayList;
import java.util.UUID;

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
          mock(ImageBackend.class), mock(FlavorBackend.class), mock(HostBackend.class),
          mock(AvailabilityZoneXenonBackend.class));
    }

    /**
     * Tests that taskBackend filter function is invoked with correct params.
     */
    @Test
    public void testTaskBackendFilterIsCalled() throws Throwable {
      Optional id = Optional.of("id");
      Optional kind = Optional.of("kind");
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      ResourceList<Task> resourceList = new ResourceList<>();
      resourceList.setItems(new ArrayList<>());
      when(taskBackend.filter(id, kind, state, pageSize)).thenReturn(resourceList);

      ResourceList result = feClient.find(id, kind, state, pageSize);
      assertThat(result, notNullValue());
      assertThat(result.getItems().size(), is(0));

      verify(taskBackend).filter(id, kind, state, pageSize);
    }

    /**
     * Tests that taskBackend getTasksPage function is called with correct params.
     * @throws Throwable
     */
    @Test
    public void testTaskBackendGetPageIsCalled() throws Throwable {
      ResourceList<Task> resourceList = new ResourceList<>();
      resourceList.setItems(new ArrayList<>());
      when(taskBackend.getTasksPage(anyString())).thenReturn(resourceList);

      String pageLink = UUID.randomUUID().toString();
      ResourceList result = feClient.getPage(pageLink);
      assertThat(result, notNullValue());
      assertThat(result.getItems().size(), is(0));

      verify(taskBackend).getTasksPage(pageLink);
    }

    @Test
    public void testGetTenantTasks() throws Throwable {
      String tenantId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getTenantTasks(tenantId, state, pageSize);
      verify(taskBackend).filter(Optional.of(tenantId), Optional.of(TenantEntity.KIND), state, pageSize);
    }

    @Test
    public void testGetResourceTicketTasks() throws Throwable {
      String resourceTicketId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getResourceTicketTasks(resourceTicketId, state, pageSize);
      verify(taskBackend).filter(Optional.of(resourceTicketId), Optional.of(ResourceTicketEntity.KIND), state,
          pageSize);
    }

    @Test
    public void testGetVmTasks() throws Throwable {
      String vmId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getVmTasks(vmId, state, pageSize);
      verify(taskBackend).filter(Optional.of(vmId), Optional.of(Vm.KIND), state, pageSize);
    }

    @Test
    public void testGetDiskTasks() throws Throwable {
      String diskId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getDiskTasks(diskId, state, pageSize);
      verify(taskBackend).filter(Optional.of(diskId), Optional.of(PersistentDisk.KIND), state, pageSize);
    }

    @Test
    public void testGetImageTasks() throws Throwable {
      String imageId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getImageTasks(imageId, state, pageSize);
      verify(taskBackend).filter(Optional.of(imageId), Optional.of(ImageEntity.KIND), state, pageSize);
    }

    @Test
    public void testGetFlavorTasks() throws Throwable {
      String flavorId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getFlavorTasks(flavorId, state, pageSize);
      verify(taskBackend).filter(Optional.of(flavorId), Optional.of(Flavor.KIND), state, pageSize);
    }

    @Test
    public void testGetHostTasks() throws Throwable {
      String hostId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getHostTasks(hostId, state, pageSize);
      verify(taskBackend).filter(Optional.of(hostId), Optional.of(HostEntity.KIND), state, pageSize);
    }

    @Test
    public void testGetAvailabilityZoneTasks() throws Throwable {
      String availabilityZoneId = "id";
      Optional state = Optional.of("state");
      Optional pageSize = Optional.of(10);

      feClient.getAvailabilityZoneTasks(availabilityZoneId, state, pageSize);
      verify(taskBackend).filter(Optional.of(availabilityZoneId), Optional.of(AvailabilityZone.KIND), state, pageSize);
    }
  }
}
