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

import com.vmware.photon.controller.apife.lib.ova.ovf.OvfFile;
import com.vmware.photon.controller.apife.lib.ova.ovf.OvfMetadata;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Test OvfConfig.
 */
public class OvfFileTest {
  private OvaTestModule ova;
  private TarFileStreamReader tarFile;
  private InputStream inputStream;

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
  public void testOvfLoad() throws IOException, ParserConfigurationException, SAXException, InterruptedException {
    setUp(OvaTestModule.GOOD_OVF_FILE_CONTENT);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    // Will throw on failure.
    try {
      OvfFile ovfFile = new OvfFile(file.content);
    } catch (Exception e) {
      fail("Unexpected exception.", e);
    }
  }

  @Test
  public void testReadOvf() throws Throwable {
    setUp(OvaTestModule.GOOD_OVF_FILE_CONTENT);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    OvfFile ovfFile = new OvfFile(file.content);
    // OVF contains one disk.
    List<OvfMetadata.VirtualDisk> disks = ovfFile.getVirtualDisks();
    assertEquals(disks.size(), 1);
    // Read controller type, expected SCSI.
    assertTrue(ovfFile.getVirtualDisks().get(0).getDiskDevice().getControllerDevice().getDescription().contains
        ("SCSI"));
  }

  @Test
  public void testReadNoOvfPrefix() throws Throwable {
    setUp(OvaTestModule.GOOD_NO_OVF_PREFIX_CONTENT);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    OvfFile ovfFile = new OvfFile(file.content);
    // OVF contains one disk.
    List<OvfMetadata.VirtualDisk> disks = ovfFile.getVirtualDisks();
    assertEquals(disks.size(), 1);
    // Read controller type, expected SCSI.
    assertTrue(ovfFile.getVirtualDisks().get(0).getDiskDevice().getControllerDevice().getDescription().contains
        ("SCSI"));
  }

  @Test
  public void testReadTwoDisk() throws Throwable {
    setUp(OvaTestModule.GOOD_OVF_TWO_DISKS);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    OvfFile ovfFile = new OvfFile(file.content);
    // OVF contains two disk.
    List<OvfMetadata.VirtualDisk> disks = ovfFile.getVirtualDisks();
    assertEquals(disks.size(), 2);
  }

  @Test
  public void testReadDiskControllerUnknown() throws Throwable {
    setUp(OvaTestModule.BAD_OVF_UNKNOWN_CONTROLLER);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    OvfFile ovfFile = new OvfFile(file.content);
    // OVF contains one disk.
    List<OvfMetadata.VirtualDisk> disks = ovfFile.getVirtualDisks();
    assertEquals(disks.size(), 1);
    // Read controller type, expected Unknown.
    assertTrue(ovfFile.getVirtualDisks().get(0).getDiskDevice().getControllerDevice().getDescription().contains
        ("Unknown"));
  }

  private void setUp(String ovfContent) throws IOException, InterruptedException {
    ova = OvaTestModule.generateOva(ovfContent);
    inputStream = ova.getOvaStream();
    tarFile = new TarFileStreamReader(inputStream);
  }

}
