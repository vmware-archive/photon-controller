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

package com.vmware.photon.controller.api.frontend.lib.image;

import com.vmware.photon.controller.api.frontend.exceptions.external.UnsupportedDiskControllerException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InvalidOvfException;
import com.vmware.photon.controller.api.frontend.lib.ova.OvaTestModule;
import com.vmware.photon.controller.api.frontend.lib.ova.TarFileStreamReader;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test ECV creator.
 */
public class EsxCloudVmxGeneratorTest {
  private static final ObjectMapper mapper = new ObjectMapper();
  private EsxOvaFile ovaFile;
  private OvaTestModule ovaTestModule;
  private InputStream inputStream;

  @AfterMethod
  public void tearDown() throws Throwable {
    if (inputStream != null) {
      inputStream.close();
      inputStream = null;
    }
    if (ovaTestModule != null) {
      ovaTestModule.clean();
      ovaTestModule = null;
    }
  }

  @Test
  public void generateECVNimbus() throws Throwable {
    setUpSimpleOva(OvaTestModule.GOOD_TESTESX_OVF_FILE_CONTENT);
    EsxCloudVmx ecv = EsxCloudVmxGenerator.generate(ovaFile.getOvf());

    assertEquals(ecv.configuration.size(), 8);
    assertEquals(ecv.configuration.get("scsi0.virtualDev"), "lsilogic");
    assertEquals(ecv.configuration.get("scsi1.virtualDev"), "lsisas1068");
    assertEquals(ecv.configuration.get("ethernet0.virtualDev"), "e1000");
    assertEquals(ecv.configuration.get("ethernet1.virtualDev"), "e1000");
    assertEquals(ecv.configuration.get("serial0.fileType"), "network");
    assertEquals(ecv.configuration.get("serial0.network.endPoint"), "client");
    assertEquals(ecv.configuration.get("serial0.yieldOnMsrRead"), "true");
    assertEquals(ecv.configuration.get("bios.bootdeviceclasses"), "deny: cd hd fd usb");
    assertEquals(ecv.parameters.size(), 2);
    assertEquals(ecv.parameters.get(0).name, "serial0.fileName"); // Present because OVF has a SerialPort device !!!!
    assertEquals(ecv.parameters.get(1).name, "serial0.vspc"); // Present because OVF has a SerialPort device !!!!
  }

  @Test(dataProvider = "GenerateECVSimpleData")
  public void generateECVSimple(String label, String content) throws Throwable {
    setUpSimpleOva(content);
    EsxCloudVmx ecv = EsxCloudVmxGenerator.generate(ovaFile.getOvf());

    assertEquals(ecv.configuration.size(), 6);
    assertEquals(ecv.configuration.get("scsi0.virtualDev"), "lsilogic");
    assertEquals(ecv.configuration.get("scsi1.virtualDev"), "buslogic");
    assertEquals(ecv.configuration.get("scsi2.virtualDev"), "lsisas1068");
    assertEquals(ecv.configuration.get("scsi3.virtualDev"), "pvscsi");
    assertEquals(ecv.configuration.get("ethernet0.virtualDev"), "e1000");
    assertEquals(ecv.configuration.get("ethernet1.virtualDev"), "e1000e");
    assertEquals(ecv.parameters.size(), 0);
  }

  @DataProvider(name = "GenerateECVSimpleData")
  Object[][] getGenerateECVSimpleData() {
    Pattern regex = Pattern.compile("ResourceSubType>(.*)<");
    Matcher regexMatcher = regex.matcher(OvaTestModule.GOOD_OVF_FILE_CONTENT);

    // change resource types to upper case
    StringBuffer upperOvf = new StringBuffer();
    while (regexMatcher.find()) {
      regexMatcher.appendReplacement(upperOvf, "ResourceSubType>" + regexMatcher.group(1).toUpperCase() + "<");
    }
    regexMatcher.appendTail(upperOvf);

    // change resource types to lower case
    StringBuffer lowerOvf = new StringBuffer();
    regexMatcher.reset();
    while (regexMatcher.find()) {
      regexMatcher.appendReplacement(lowerOvf, "ResourceSubType>" + regexMatcher.group(1).toUpperCase() + "<");
    }
    regexMatcher.appendTail(lowerOvf);

    return new Object[][]{
        {"original", OvaTestModule.GOOD_OVF_FILE_CONTENT},
        {"upper-case", upperOvf.toString()},
        {"lower-case", lowerOvf.toString()}
    };
  }

  @Test
  public void generateECVSimpleTwoDisks() throws Throwable {
    setUpSimpleOva(OvaTestModule.GOOD_OVF_TWO_DISKS);
    EsxCloudVmx ecv = EsxCloudVmxGenerator.generate(ovaFile.getOvf());

    assertEquals(ecv.configuration.size(), 2);
    assertEquals(ecv.configuration.get("scsi0.virtualDev"), "lsilogic");
    assertEquals(ecv.configuration.get("ethernet0.virtualDev"), "e1000");
    assertEquals(ecv.parameters.size(), 0);
  }


  @Test
  public void generateECVBad() throws Throwable {
    try {
      setUpSimpleOva(OvaTestModule.BAD_OVF_UNKNOWN_CONTROLLER);
    } catch (UnsupportedDiskControllerException e) {
      return;
    }
    fail("Exception expected.");
  }

  @Test
  public void generateUnknownDevice() throws Throwable {
    OvaTestModule ova = OvaTestModule.generateOva(OvaTestModule.BAD_OVF_UNKNOWN_CONTROLLER);
    inputStream = ova.getOvaStream();
    try {
      EsxCloudVmxGenerator.generate(new OvfFile(
          new TarFileStreamReader(inputStream).iterator().next().content));
    } catch (InvalidOvfException e) {
      return;
    } finally {
      ova.clean();
    }
    fail("Exception expected.");
  }

  private void setUpSimpleOva(String ovfContent) throws Throwable {
    ovaTestModule = OvaTestModule.generateOva(ovfContent);
    inputStream = ovaTestModule.getOvaStream();
    ovaFile = new EsxOvaFile(inputStream);
  }
}
