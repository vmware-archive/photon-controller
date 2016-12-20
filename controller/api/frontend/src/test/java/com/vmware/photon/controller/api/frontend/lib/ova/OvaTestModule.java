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

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Random;

/**
 * Test helper module.
 */
public class OvaTestModule {
  public static final String GOOD_OVF_FILE_CONTENT;
  public static final String GOOD_TESTESX_OVF_FILE_CONTENT;
  public static final String GOOD_OVF_TWO_DISKS;
  public static final String GOOD_OVF_BIG;
  public static final String BAD_OVF_UNKNOWN_CONTROLLER;
  public static final String VM_COLLECTION_1_OVF;
  public static final String VM_COLLECTION_2_OVF;
  public static final String GOOD_NO_OVF_PREFIX_CONTENT;


  /**
   * Partial static constructor.
   */
  static {
    try {
      URL url = OvaTestModule.class.getResource("/ovf/good.ovf");
      GOOD_OVF_FILE_CONTENT = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/testesx_pre_poweron.ovf");
      GOOD_TESTESX_OVF_FILE_CONTENT = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/badTwoDisks.ovf");
      GOOD_OVF_TWO_DISKS = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/badUnknownController.ovf");
      BAD_OVF_UNKNOWN_CONTROLLER = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/big.ovf");
      GOOD_OVF_BIG = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/VMCollection_1.ovf");
      VM_COLLECTION_1_OVF = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/VMCollection_2.ovf");
      VM_COLLECTION_2_OVF = FileUtils.readFileToString(new File(url.getPath()));
      url = OvaTestModule.class.getResource("/ovf/goodNoOvfPrefix.ovf");
      GOOD_NO_OVF_PREFIX_CONTENT = FileUtils.readFileToString(new File(url.getPath()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final int BIG_PRIME_NUMBER = 104729;
  public final String ovaName;
  public final String ovfName;
  public final String ovfContent;
  public final String vmdkName;
  public final String vmdkContent;
  private final String fileLocation;
  private boolean clean = false;

  public OvaTestModule(String ovaName, String ovfName, String ovfContent, String vmdkName)
      throws IOException, InterruptedException {
    this.ovaName = ovaName;
    this.ovfName = ovfName;
    this.ovfContent = ovfContent;
    this.vmdkName = vmdkName;
    this.fileLocation = Files.createTempDir().getAbsolutePath();

    this.vmdkContent = generateVmdkContent();
    generateOvaFile(ovaName, ovfName, ovfContent, vmdkName, vmdkContent);
  }

  public static OvaTestModule generateOva(String ovfContent) throws IOException, InterruptedException {
    return new OvaTestModule(
        "test.ova",
        "test.ovf",
        ovfContent,
        "test.vmdk");
  }

  /**
   * Read a binary stream as string.
   *
   * @param stream
   * @return
   * @throws java.io.IOException
   */
  public static String readStringFromStream(InputStream stream) throws IOException {
    ByteArrayOutputStream byteString = new ByteArrayOutputStream();
    int c;
    int i = 0;
    while ((c = stream.read()) != -1) {
      byteString.write(c);
      byte[] b = new byte[i + i];
      int readBytes = stream.read(b, i, i);
      for (int k = 0; k < readBytes; k++) {
        byteString.write(b[k + i]);
      }
      if (readBytes != i) {
        i = i / 2;
      } else {
        i = i + 1;
      }
    }
    return byteString.toString();
  }

  public InputStream getRawVmdkStream() {
    return new ByteArrayInputStream(vmdkContent.getBytes());
  }

  public InputStream getOvaStream() throws FileNotFoundException {
    return new FileInputStream(new File(fileLocation, ovaName));
  }

  public void clean() throws Throwable {
    clean = true;
    File tmpDir = new File(fileLocation);
    if (tmpDir.exists()) {
      FileUtils.deleteDirectory(tmpDir);
    }
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!clean) {
      clean();
    }
  }

  private String generateVmdkContent() {
    return "KDM\ncreateType = \"streamOptimized\"\n" + buildRandomString(BIG_PRIME_NUMBER);
  }

  private void generateOvaFile(String ovaName, String ovfName, String ovfContent, String vmdkName,
                               String vmdkContent) throws IOException, InterruptedException {
    PrintWriter out = new PrintWriter(new File(fileLocation, ovfName));
    out.print(ovfContent);
    out.close();
    out = new PrintWriter(new File(fileLocation, vmdkName));
    out.print(vmdkContent);
    out.close();
    Runtime.getRuntime().exec(
        String.format("tar cvf %s %s %s --format ustar", ovaName, ovfName, vmdkName),
        null,
        new File(fileLocation)).waitFor();
  }

  /**
   * Generate an array of 'size' printable characters.
   *
   * @param size
   * @return
   */
  private String buildRandomString(int size) {
    Random generator = new Random();
    ByteArrayOutputStream byteString = new ByteArrayOutputStream();
    for (int i = 0; i < size; i++) {
      byteString.write(' ' + (byte) generator.nextInt('~' - ' '));
    }
    return byteString.toString();
  }
}
