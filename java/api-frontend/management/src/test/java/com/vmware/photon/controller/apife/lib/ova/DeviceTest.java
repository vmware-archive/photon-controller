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

package com.vmware.photon.controller.apife.lib.ova;

import com.vmware.photon.controller.apife.exceptions.internal.InvalidOvfException;
import com.vmware.photon.controller.apife.lib.image.EsxOvaFile;
import com.vmware.photon.controller.apife.lib.ova.ovf.Device;
import com.vmware.photon.controller.apife.lib.ova.ovf.OvfFile;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.util.Map;

/**
 * Test device class.
 */
public class DeviceTest {

  private OvfFile ovfFile;

  private OvaTestModule ova;
  private InputStream inputStream;

  @BeforeTest
  public void setUp() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_FILE_CONTENT);
    inputStream = ova.getOvaStream();
    ovfFile = new EsxOvaFile(inputStream).getOvf();
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    if (inputStream != null) {
      inputStream.close();
      inputStream = null;
    }
    if (ova != null) {
      ova.clean();
      ova = null;
    }
  }

  @Test
  public void testFindDisk() throws Throwable {
    for (Device device : ovfFile.getDevices()) {
      if (device.getDeviceType() == Device.DeviceType.DiskDrive) {
        return;
      }
    }
    fail("Disk not found");
  }

  @Test
  public void testFindDiskController() throws Throwable {
    for (Device device : ovfFile.getDevices()) {
      if (device.getDeviceType() == Device.DeviceType.DiskDrive) {
        Device controller = device.getControllerDevice();
        assertEquals(controller.getDeviceType(), Device.DeviceType.SCSIController);
        return;
      }
    }
    fail("Disk not found");
  }

  @Test(expectedExceptions = InvalidOvfException.class)
  public void testReadBadItemConfig() throws Throwable {
    for (Device device : ovfFile.getDevices()) {
      if (device.getDeviceType() == Device.DeviceType.SCSIController) {
        device.getConfig();
        return;
      }
    }
    fail("Exception expected.");
  }

  @Test
  public void testReadGoodItemConfig() throws Throwable {
    for (Device device : ovfFile.getDevices()) {
      if (device.getDeviceType() == Device.DeviceType.DiskDrive) {
        Map<String, String> map = device.getConfig();
        assertEquals(map.size(), 1);
        assertEquals(map.get("test key"), "test value");
        return;
      }
    }
    fail("Disk not found");
  }
}
