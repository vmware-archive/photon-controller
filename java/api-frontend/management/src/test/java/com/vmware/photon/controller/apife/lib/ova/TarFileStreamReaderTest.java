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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tests {@link TarFileStreamReader}.
 */
public class TarFileStreamReaderTest {
  private OvaTestModule ova;
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
  public void testExpectTwoFiles() throws Throwable {
    ova = OvaTestModule.generateOva("ovf content");
    inputStream = ova.getOvaStream();
    TarFileStreamReader tarFile = new TarFileStreamReader(inputStream);
    int i = 0;
    for (TarFileStreamReader.TarFile file : tarFile) {
      OvaTestModule.readStringFromStream(file.content);
      i += 1;
    }
    assertEquals(i, 2);
  }

  @Test
  public void failToFindFiles() throws Throwable {
    ova = OvaTestModule.generateOva("ovf content");
    TarFileStreamReader badTarFile = new TarFileStreamReader(new ByteArrayInputStream(ova.vmdkContent.getBytes()));
    assertNull(badTarFile.iterator().next());
  }

  @Test
  public void testReadFileContent() throws Throwable {
    ova = OvaTestModule.generateOva("ovf content");
    inputStream = ova.getOvaStream();
    TarFileStreamReader tarFile = new TarFileStreamReader(inputStream);
    TarFileStreamReader.TarFile file = tarFile.iterator().next();
    assertNotNull(file);
    String content = OvaTestModule.readStringFromStream(file.content);
    assertEquals(content, ova.ovfContent);
    file = tarFile.iterator().next();
    assertNotNull(file);
    content = OvaTestModule.readStringFromStream(file.content);
    assertEquals(content, ova.vmdkContent);
  }

  @Test
  public void testFailToReadFileContent() throws Throwable {
    ova = OvaTestModule.generateOva("ovf content");
    TarFileStreamReader badTarFile = new TarFileStreamReader(new ByteArrayInputStream(ova.vmdkContent.getBytes()));
    TarFileStreamReader.TarFile file = badTarFile.iterator().next();
    assertNull(file);
  }

  @Test
  public void checkReadLengthField() throws IOException, InterruptedException {
    ova = OvaTestModule.generateOva(OvaTestModule.GOOD_OVF_BIG);
    inputStream = ova.getOvaStream();
    TarFileStreamReader tar = new TarFileStreamReader(inputStream);

    String ovf1 = OvaTestModule.readStringFromStream(tar.iterator().next().content);
    String ovf2 = OvaTestModule.readStringFromStream(new ByteArrayInputStream(OvaTestModule.GOOD_OVF_BIG.getBytes()));
    assertEquals(ovf1, ovf2);
  }

  private void deleteFileIfExists(String path, String file) {
    String fileName;
    if (!file.isEmpty()) {
      fileName = String.format("%s/%s", path, file);
    } else {
      fileName = path;
    }
    File f = new File(fileName);
    if (f.exists()) {
      f.delete();
    }
  }
}
