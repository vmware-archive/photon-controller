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

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.photon.controller.resource.gen.ImageType;

/**
 * Class to generate the image manifest.
 */
public class ImageManifestGenerator {

  /**
   * Generate image manifest JSON.
   *
   * @param imageEntity
   * @return
   */
  public static ImageManifest generate(ImageEntity imageEntity) {

    // Set image defaults.
    ImageManifest imageManifest = new ImageManifest();
    imageManifest.imageType = ImageType.CLOUD;
    ImageReplicationType replicationType = imageEntity.getReplicationType();
    switch (replicationType) {
      case ON_DEMAND:
        imageManifest.imageReplication = ImageReplication.ON_DEMAND;
        break;
      case EAGER:
        imageManifest.imageReplication = ImageReplication.EAGER;
        break;
      default:
        throw new IllegalArgumentException("Unkown image replication type" + replicationType);
    }

    return imageManifest;
  }
}
