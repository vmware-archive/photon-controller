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

package com.vmware.photon.controller.api.frontend.lib.ova;

import com.vmware.photon.controller.api.frontend.lib.image.EsxOvaFile;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.Device;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfFile;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfMetadata;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;

import java.io.InputStream;
/**
 * Test OvfMetadata class.
 */
public class OvfMetadataTest {

  private OvfFile ovfFile;

  private OvaTestModule ova;
  private InputStream inputStream;

  @BeforeMethod
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
    for (OvfMetadata.VirtualDisk vdisk : ovfFile.getVirtualDisks()) {
      assertEquals(vdisk.getDiskDevice().getDeviceType(), Device.DeviceType.DiskDrive);
    }
  }

  @Test
  public void testFileReference() throws Throwable {
    for (OvfMetadata.FileReference file : ovfFile.getFileReferences()) {
      assertThat("Expect disk file.", file.getFileName().endsWith(".vmdk"));
    }
  }

  @Test
  public void testVirtualNetwork() throws Throwable {
    assertThat("Network should be present.", ovfFile.getVirtualNetworks().size() >= 1);
  }
}
