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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.Iso;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link EntityLockDcpBackend}.
 */
public class EntityLockDcpBackendTest {

  private static ApiFeXenonRestClient dcpClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    dcpClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (dcpClient == null) {
      throw new IllegalStateException(
          "dcpClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (dcpClient != null) {
      dcpClient.stop();
      dcpClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for setTaskLock.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class SetTaskLockTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private EntityLockDcpBackend entityLockDcpBackend;

    private TaskEntity taskEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      taskEntity = new TaskEntity();
      taskEntity.setId("task-id");
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @DataProvider(name = "getEntities")
    private Object[][] getEntities() {
      return new Object[][]{
          {Vm.KIND},
          {PersistentDisk.KIND},
          {EphemeralDisk.KIND},
          {Image.KIND},
          {Iso.KIND}
      };
    }

    @Test(dataProvider = "getEntities")
    public void testSetTaskLockSuccess(String entityKind) throws Throwable {
      BaseEntity entity = null;
      switch (entityKind) {
        case (Vm.KIND):
          entity = new VmEntity();
          entity.setId("vm-id");
          break;
        case (PersistentDisk.KIND):
          entity = new PersistentDiskEntity();
          entity.setId("persistent-id");
          break;
        case (EphemeralDisk.KIND):
          entity = new EphemeralDiskEntity();
          entity.setId("ephemeral-id");
          break;
        case (Image.KIND):
          entity = new ImageEntity();
          entity.setId("image-id");
          break;
        case (Iso.KIND):
          entity = new IsoEntity();
          entity.setId("iso-id");
          break;
        default:
          break;
      }

      entityLockDcpBackend.setTaskLock(entity.getId(), taskEntity);
      //acquiring lock on the same task should be no-op
      entityLockDcpBackend.setTaskLock(entity.getId(), taskEntity);

      try {
        TaskEntity taskEntityOther = new TaskEntity();
        taskEntityOther.setId("task-id-other");

        entityLockDcpBackend.setTaskLock(entity.getId(), taskEntityOther);
        fail("should have failed with ConcurrentTaskException");
      } catch (ConcurrentTaskException ignored) {
      }
    }

    @Test
    public void testSetLockNullEntity() throws Throwable {
      try {
        entityLockDcpBackend.setTaskLock(null, new TaskEntity());
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Entity cannot be null."));
      }
    }

    @Test
    public void testSetLockNullTask() throws Throwable {
      try {
        entityLockDcpBackend.setTaskLock("dummy-id", null);
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("TaskEntity cannot be null."));
      }
    }
  }

  /**
   * Tests for cleaning lock.
   */
  @Guice(modules = {DcpBackendTestModule.class, TestModule.class})
  public static class ClearLocksTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private EntityLockDcpBackend entityLockDcpBackend;

    private TaskEntity taskEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      taskEntity = new TaskEntity();
      taskEntity.setId("task-id");
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testClearLockSuccess() throws Throwable {
      String vmId = UUID.randomUUID().toString();
      String ephemeralDiskId = UUID.randomUUID().toString();
      entityLockDcpBackend.setTaskLock(vmId, taskEntity);
      entityLockDcpBackend.setTaskLock(ephemeralDiskId, taskEntity);

      entityLockDcpBackend.clearTaskLocks(taskEntity);
      // Now the lock has been cleared, should be able to set locks again

      entityLockDcpBackend.setTaskLock(vmId, taskEntity);
      entityLockDcpBackend.setTaskLock(ephemeralDiskId, taskEntity);
    }


    @Test
    public void testClearLockSuccessForLocksCreatedFailed() throws Throwable {
      String vmId = UUID.randomUUID().toString();
      String ephemeralDiskId = UUID.randomUUID().toString();
      TaskEntity taskEntity2 = new TaskEntity();
      taskEntity2.setId("task-id2");

      entityLockDcpBackend.setTaskLock(vmId, taskEntity2);
      entityLockDcpBackend.setTaskLock(ephemeralDiskId, taskEntity2);

      try {
        entityLockDcpBackend.setTaskLock(vmId, taskEntity);
      } catch (Exception e) {
        assertThat(taskEntity.getLockedEntityIds().size(), is(1));
      }

      try {
        entityLockDcpBackend.setTaskLock(ephemeralDiskId, taskEntity);
      } catch (Exception e) {
        assertThat(taskEntity.getLockedEntityIds().size(), is(2));
      }

      entityLockDcpBackend.clearTaskLocks(taskEntity);
      assertThat(taskEntity.getLockedEntityIds().size(), is(0));

      entityLockDcpBackend.clearTaskLocks(taskEntity2);
      // Now the lock has been cleared, should be able to set locks again

      entityLockDcpBackend.setTaskLock(vmId, taskEntity);
      entityLockDcpBackend.setTaskLock(ephemeralDiskId, taskEntity);
      entityLockDcpBackend.clearTaskLocks(taskEntity);

      //When another delete is issued, it should not throw Exception
      entityLockDcpBackend.clearTaskLocks(taskEntity);
    }

    @Test
    public void testClearLockNullStep() throws Throwable {
      try {
        entityLockDcpBackend.clearTaskLocks(null);
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("TaskEntity cannot be null."));
      }
    }
  }

}
