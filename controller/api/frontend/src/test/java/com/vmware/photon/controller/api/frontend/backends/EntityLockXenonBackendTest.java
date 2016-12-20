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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.TestModule;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.ImageEntity;
import com.vmware.photon.controller.api.frontend.entities.IsoEntity;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.Iso;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.HttpURLConnection;
import java.util.UUID;

/**
 * Tests {@link EntityLockXenonBackend}.
 */
public class EntityLockXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
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
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
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
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class SetTaskLockTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

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

      entityLockXenonBackend.setTaskLock(entity, taskEntity);
      //acquiring lock on the same task should be no-op
      entityLockXenonBackend.setTaskLock(entity, taskEntity);

      try {
        TaskEntity taskEntityOther = new TaskEntity();
        taskEntityOther.setId("task-id-other");

        entityLockXenonBackend.setTaskLock(entity, taskEntityOther);
        fail("should have failed with ConcurrentTaskException");
      } catch (ConcurrentTaskException ignored) {
      }
    }

    @Test
    public void testSetLockNullEntity() throws Throwable {
      try {
        entityLockXenonBackend.setTaskLock(null, new TaskEntity());
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Entity cannot be null."));
      }
    }

    @Test
    public void testSetLockNullTask() throws Throwable {
      try {
        VmEntity entity = new VmEntity();
        entityLockXenonBackend.setTaskLock(entity, null);
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("TaskEntity cannot be null."));
      }
    }
  }

  /**
   * Tests for cleaning lock.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class ClearLocksTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    private TaskEntity taskEntity;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      entityLockXenonBackend = new EntityLockXenonBackend(xenonClient);
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
      VmEntity vmEntity = new VmEntity();
      vmEntity.setId(UUID.randomUUID().toString());
      EphemeralDiskEntity diskEntity = new EphemeralDiskEntity();
      diskEntity.setId(UUID.randomUUID().toString());
      entityLockXenonBackend.setTaskLock(vmEntity, taskEntity);
      entityLockXenonBackend.setTaskLock(diskEntity, taskEntity);

      entityLockXenonBackend.clearTaskLocks(taskEntity);
      // Now the lock has been cleared, should be able to set locks again

      entityLockXenonBackend.setTaskLock(vmEntity, taskEntity);
      entityLockXenonBackend.setTaskLock(diskEntity, taskEntity);
    }


    @Test
    public void testClearLockSuccessForLocksCreatedFailed() throws Throwable {
      VmEntity vmEntity = new VmEntity();
      vmEntity.setId(UUID.randomUUID().toString());
      TaskEntity taskEntity2 = new TaskEntity();
      taskEntity2.setId("task-id2");
      TaskEntity taskEntity3 = new TaskEntity();

      entityLockXenonBackend.setTaskLock(vmEntity, taskEntity);

      try {
        entityLockXenonBackend.setTaskLock(vmEntity, taskEntity2);
        Assert.fail("acquiring lock for an already owned lock should have failed");
      } catch (Exception e) {
        assertThat(e, is(instanceOf(ConcurrentTaskException.class)));
      }

      assertThat(taskEntity.getLockedEntityIds().size(), is(1));
      assertThat(taskEntity2.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity3.getLockedEntityIds().size(), is(0));

      try {
        entityLockXenonBackend.setTaskLock(vmEntity, taskEntity3);
        Assert.fail("acquiring lock for an already owned lock should have failed");
      } catch (ConcurrentTaskException e) {
        Assert.fail("acquiring lock an invalid lock request that is missing ownerTaskId should not fail with " +
            "ConcurrentTaskException");
      } catch (XenonRuntimeException e) {
        assertThat(e.getCompletedOperation().getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
      } catch (Exception e) {
        Assert.fail("acquiring lock should not have failed with an unknown exception");
      }

      assertThat(taskEntity.getLockedEntityIds().size(), is(1));
      assertThat(taskEntity2.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity3.getLockedEntityIds().size(), is(1));

      entityLockXenonBackend.clearTaskLocks(taskEntity2);

      assertThat(taskEntity.getLockedEntityIds().size(), is(1));
      assertThat(taskEntity2.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity3.getLockedEntityIds().size(), is(1));

      entityLockXenonBackend.clearTaskLocks(taskEntity3);

      assertThat(taskEntity.getLockedEntityIds().size(), is(1));
      assertThat(taskEntity2.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity3.getLockedEntityIds().size(), is(1));

      entityLockXenonBackend.clearTaskLocks(taskEntity);

      assertThat(taskEntity.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity2.getLockedEntityIds().size(), is(0));
      assertThat(taskEntity3.getLockedEntityIds().size(), is(1));
    }

    @Test
    public void testClearLockNullStep() throws Throwable {
      try {
        entityLockXenonBackend.clearTaskLocks(null);
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("TaskEntity cannot be null."));
      }
    }
  }

}
