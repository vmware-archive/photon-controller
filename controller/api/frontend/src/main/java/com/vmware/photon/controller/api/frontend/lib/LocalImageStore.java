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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.frontend.exceptions.internal.DeleteUploadFolderException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * This class is to save the image in local file system. It's an interim implementation.
 */
public class LocalImageStore implements ImageStore {
  private static final Logger logger = LoggerFactory.getLogger(LocalImageStore.class);
  private final String dir;
  private final String datastore;

  public LocalImageStore(String dir, String datastore) {
    this.datastore = datastore;

    File theDir = new File(dir, datastore);
    if (!theDir.exists()) {
      theDir.mkdirs();
    }
    this.dir = theDir.getAbsolutePath();
  }

  @Override
  public void setHostIp(String hostIp) {
  }

  @Override
  public Image createImage(String imageId) throws InternalException {
    return new LocalImageStoreImage(dir, imageId);
  }

  @Override
  public void finalizeImage(Image image) {
    logger.debug("LocalImageStore finalizeImage {}", image.getImageId());
  }

  @Override
  public void createImageFromVm(Image image, String vmId) {
    logger.debug("LocalImageStore createImageFromVm {}", image.getImageId());
  }

  @Override
  public void deleteUploadFolder(Image image) throws DeleteUploadFolderException {
    // To be implemented if it is needed in the future.
    throw new DeleteUploadFolderException("deleteUploadFolder method has not been " +
        "implemented for class LocalImageStore.");
  }

  @Override
  public boolean isReplicationNeeded() {
    return false;
  }

  @Override
  public String getDatastore() {
    return datastore;
  }
}
