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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Tests {@link VmdkMetadata}.
 */
public class VmdkMetadataTest {

  private InputStream fis;

  @AfterMethod
  public void tearDown() throws Throwable {
    try {
      if (fis != null) {
        fis.close();
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  @Test
  public void testGetSingleExtentSize() throws Exception {
    File file = new File(VmdkMetadataTest.class.getResource("/vmdk/good.vmdk").getPath());
    fis = new BufferedInputStream(new FileInputStream(file));
    assertThat(VmdkMetadata.getSingleExtentSize(fis), is(4096));

    // Ensure it works, even if the extent filename has a space in it.
    file = new File(VmdkMetadataTest.class.getResource("/vmdk/good-space.vmdk").getPath());
    fis = new BufferedInputStream(new FileInputStream(file));
    assertThat(VmdkMetadata.getSingleExtentSize(fis), is(4096));
  }

  @DataProvider(name = "extents")
  public Object[][] createDefault() {
    return new Object[][] { { "RW 4096 SPARSE \"file.vmdk\"", 4, "4096", "file.vmdk" },
        { "RW 4096 SPARSE \"file 2.vmdk\"", 4, "4096", "file 2.vmdk" },
        { "  RW 4096 SPARSE \"file.vmdk\"", 4, "4096", "file.vmdk" },
        { "  RW 4096 SPARSE \"file 2.vmdk\"", 4, "4096", "file 2.vmdk" },
        { "RW 4096 SPARSE   \"file.vmdk\" 0", 5, "4096", "file.vmdk" },
        { "RW 4096 SPARSE   \"file.vmdk\" 0", 5, "4096", "file.vmdk" }, };
  }

  /**
   * The extractExtentFields() relies on a slightly complicated regular expression. We test the regular expression
   * indirectly by testing extractExtentFields.
   */
  @Test(dataProvider = "extents")
  public void testExtentTokenRegex(
      String extentDescription,
      int expectedNumberOfFields,
      String expectedSize,
      String expectedFilename) throws Exception {
    List<String> extentFields = VmdkMetadata.extractExtentFields(extentDescription);
    assertThat(extentFields.size(), is(expectedNumberOfFields));
    assertThat(extentFields.get(1), is(expectedSize));
    assertThat(extentFields.get(3), is(expectedFilename));
  }

  @Test(dataProvider = "BadVmdk")
  public void testBadVmdkError(InputStream inputStream, String errorMsg) throws Exception {
    fis = inputStream;
    try {
      VmdkMetadata.getSingleExtentSize(fis);
      fail("parsing vmdk should fail");
    } catch (IllegalStateException ex) {
      assertThat(ex.getMessage(), containsString(errorMsg));
    }
  }

  @DataProvider(name = "BadVmdk")
  Object[][] getBadVmdk() throws Throwable {
    File file = new File(VmdkMetadataTest.class.getResource("/vmdk/badMultipleDataFields.vmdk").getPath());
    return new Object[][]{
        {new BufferedInputStream(new FileInputStream(file)), "Invalid vmdk: multiple vmdks detected"},
        {new ByteArrayInputStream("  ".getBytes()), "Invalid vmdk: missing # Extent description"},
        {new ByteArrayInputStream("# Extent description ".getBytes()), "Invalid vmdk: missing # The Disk Data Base"},
        {new ByteArrayInputStream("# Extent description\nRW 4096 SPARSE\n# The Disk Data Base".getBytes()),
            "Invalid vmdk: fields length is 3"},
        {new ByteArrayInputStream(
            "# Extent description\nRW xx SPARSE \"2mb_monosparse.vmdk\"\n# The Disk Data Base".getBytes()),
            "Invalid string: second field is not integer"}
    };
  }
}
