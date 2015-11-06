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

import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.photon.controller.resource.gen.ImageType;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.io.IOException;

/**
 * Test image manifest generator.
 */
public class ImageManifestGeneratorTest {

  @Test
  public void testDefaultManifest() throws IOException {
    ImageManifest manifest = ImageManifestGenerator.generate(new ImageEntity());
    assertEquals(manifest.imageType, ImageType.CLOUD);
    assertEquals(manifest.imageReplication, ImageReplication.EAGER);
  }
}
