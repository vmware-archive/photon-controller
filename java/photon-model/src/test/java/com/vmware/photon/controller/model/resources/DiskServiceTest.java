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

package com.vmware.photon.controller.model.resources;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.model.helpers.BaseModelTest;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link DiskService} class.
 */
public class DiskServiceTest {

  private DiskService.Disk buildValidStartState() throws Throwable {
    DiskService.Disk disk = new DiskService.Disk();

    disk.id = UUID.randomUUID().toString();
    disk.type = DiskService.DiskType.HDD;
    disk.name = "friendly-name";
    disk.capacityMBytes = 100L;

    return disk;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private DiskService diskService;

    @BeforeMethod
    public void setUpTest() {
      diskService = new DiskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(diskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {

    @Test
    public void testValidStartState() throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.type, is(startState.type));
      assertThat(returnState.capacityMBytes, is(startState.capacityMBytes));
    }

    @Test
    public void testMissingId() throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.id = null;

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingName() throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.name = null;

      machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingType() throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.type = null;

      machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);
    }

    @Test(dataProvider = "capacityMBytes")
    public void testCapacityLessThanOneMB(Long capacityMBytes) throws Throwable{
      DiskService.Disk startState = buildValidStartState();
      startState.capacityMBytes = capacityMBytes;
      startState.sourceImageReference = new URI("http://sourceImageReference");
      startState.customizationServiceReference = new URI("http://customizationServiceReference");

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      assertNotNull(returnState);
    }

    @DataProvider(name = "capacityMBytes")
    public Object[][] getCapacityMBytes() {
      return new Object[][] {
          {1L},
          {0L}
      };
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingSourceImageReferenceAndCustomizationServiceReferenceWhenCapacityLessThanOneMB()
        throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.capacityMBytes = 0;
      startState.sourceImageReference = null;
      startState.customizationServiceReference = null;

      machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);
    }

    @Test
    public void testMissingStatus() throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.status = null;

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      assertNotNull(returnState);
      assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
    }

    @Test(dataProvider = "fileEntryPaths", expectedExceptions = IllegalArgumentException.class)
    public void testMissingPathInFileEntry(String path) throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.bootConfig = new DiskService.Disk.BootConfig();
      startState.bootConfig.files = new DiskService.Disk.BootConfig.FileEntry[1];
      startState.bootConfig.files[0] = new DiskService.Disk.BootConfig.FileEntry();
      startState.bootConfig.files[0].path = path;

      machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);
    }

    @DataProvider(name = "fileEntryPaths")
    public Object[][] getFileEntryPaths() {
      return new Object[][] {
          {null},
          {""}
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest extends BaseModelTest {

    @Test(dataProvider = "patchZoneId")
    public void testPatchZoneId(String patchValue) throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.zoneId = "startZoneId";

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      DiskService.Disk patchState = new DiskService.Disk();
      patchState.zoneId = patchValue;

      Operation operation = machine.sendPatchAndWait(returnState.documentSelfLink, patchState);

      if (startState.zoneId.equals(patchValue)) {
        assertThat(operation.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      } else {
        returnState = operation.getBody(DiskService.Disk.class);
        assertThat(returnState.zoneId, is(patchValue));
      }
    }

    @DataProvider(name = "patchZoneId")
    public Object[][] getPatchZoneId() {
      return new Object[][] {
          {null},
          {"startZoneId"},
          {"patchZoneId"}
      };
    }

    @Test(dataProvider = "patchName")
    public void testPatchName(String patchValue) throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.name = "startName";

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      DiskService.Disk patchState = new DiskService.Disk();
      patchState.name = patchValue;

      Operation operation = machine.sendPatchAndWait(returnState.documentSelfLink, patchState);

      if (startState.name.equals(patchValue)) {
        assertThat(operation.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      } else {
        returnState = operation.getBody(DiskService.Disk.class);
        assertThat(returnState.name, is(patchValue));
      }
    }

    @DataProvider(name = "patchName")
    public Object[][] getPatchName() {
      return new Object[][] {
          {null},
          {"startZoneId"},
          {"patchZoneId"}
      };
    }

    @Test(dataProvider = "patchStatus")
    public void testPatchStatus(DiskService.DiskStatus patchValue) throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.status = DiskService.DiskStatus.DETACHED;

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      DiskService.Disk patchState = new DiskService.Disk();
      patchState.status = patchValue;

      Operation operation = machine.sendPatchAndWait(returnState.documentSelfLink, patchState);

      if (startState.status.equals(patchValue)) {
        assertThat(operation.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      } else {
        returnState = operation.getBody(DiskService.Disk.class);
        assertThat(returnState.status, is(patchValue));
      }
    }

    @DataProvider(name = "patchStatus")
    public Object[][] getPatchStatus() {
      return new Object[][] {
          {null},
          {DiskService.DiskStatus.DETACHED},
          {DiskService.DiskStatus.ATTACHED}
      };
    }

    @Test(dataProvider = "patchCapacityMBytes")
    public void testPatchCapacityMBytes(Long patchValue) throws Throwable {
      DiskService.Disk startState = buildValidStartState();
      startState.capacityMBytes = 100L;

      DiskService.Disk returnState = machine.callServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.Disk.class);

      DiskService.Disk patchState = new DiskService.Disk();
      patchState.capacityMBytes = patchValue;

      Operation operation = machine.sendPatchAndWait(returnState.documentSelfLink, patchState);

      if (startState.capacityMBytes == patchValue || patchValue == 0L) {
        assertThat(operation.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));
      } else {
        returnState = operation.getBody(DiskService.Disk.class);
        assertThat(returnState.capacityMBytes, is(patchValue));
      }
    }

    @DataProvider(name = "patchCapacityMBytes")
    public Object[][] getPatchCapacityMBytes() {
      return new Object[][] {
          {0L},
          {100L},
          {200L}
      };
    }
  }
}
