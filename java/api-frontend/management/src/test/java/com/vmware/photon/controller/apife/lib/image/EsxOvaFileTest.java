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

package com.vmware.photon.controller.apife.lib.image;

import com.vmware.photon.controller.apife.exceptions.external.InvalidOvaException;
import com.vmware.photon.controller.apife.exceptions.external.UnsupportedDiskControllerException;
import com.vmware.photon.controller.apife.lib.ova.OvaTestModule;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Test ovf validator.
 */
public class EsxOvaFileTest {
  private OvaTestModule ova;
  private InputStream inputStream;

  @AfterMethod
  public void tearDown() throws Throwable {
    if (ova != null) {
      ova.clean();
      ova = null;
    }
  }

  @Test
  public void testValidatorGood() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_FILE_CONTENT);

    try {
      inputStream = ova.getOvaStream();
      new EsxOvaFile(inputStream);
    } catch (RuntimeException e) {
      fail("Unexpected exception.", e);
    }
  }

  /**
   * Test parsing a file that is not OVA or VMDK.
   */
  @Test
  public void testNonOvaFormatFileError() throws Throwable {
    File tmpFile = File.createTempFile("temp-file-name", ".tmp");
    try {
      new EsxOvaFile(new FileInputStream(tmpFile.getPath()));
      fail("TarFileStreamReader.TarFile file should be null and throw exception");
    } catch (InvalidOvaException e) {
      assertEquals(e.getMessage(), "Invalid OVA: OVF file not found.");
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void testVmdkFirst() throws Throwable {
    ova = new OvaTestModule("test.ova", "test.vmdk", OvaTestModule.GOOD_OVF_FILE_CONTENT, "test.ovf");

    try {
      inputStream = ova.getOvaStream();
      new EsxOvaFile(inputStream);
      fail("Expected exception.");
    } catch (InvalidOvaException e) {
      assertEquals(e.getMessage(), "Invalid OVA: OVF file not found.");
    }
  }

  @Test
  public void testValidatorTwoDisks() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_TWO_DISKS);

    try {
      inputStream = ova.getOvaStream();
      new EsxOvaFile(inputStream);
    } catch (RuntimeException e) {
      fail("Two disks should be supported.");
    }
  }

  @Test
  public void testValidatorUnknownController() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.BAD_OVF_UNKNOWN_CONTROLLER);

    try {
      inputStream = ova.getOvaStream();
      new EsxOvaFile(inputStream);
    } catch (UnsupportedDiskControllerException e) {
      return;
    }
    fail("Unexpected success.");
  }

  @Test
  public void testVirtualCollection1Ovf() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.VM_COLLECTION_1_OVF);

    inputStream = ova.getOvaStream();
    EsxOvaFile esxOva = new EsxOvaFile(inputStream);
    assertTrue(esxOva.getOvf().getDevices().size() > 0, "Devices should be found.");
  }

  @Test
  public void testVirtualCollection2Ovf() throws Throwable {
    ova = OvaTestModule.generateOva(OvaTestModule.VM_COLLECTION_2_OVF);

    try {
      inputStream = ova.getOvaStream();
      EsxOvaFile esxOva = new EsxOvaFile(inputStream);
      fail("Exception expected.");
    } catch (RuntimeException e) {
      assertEquals(e.getMessage(), "OVF with more than one VM not supported.");
    }
  }
}
