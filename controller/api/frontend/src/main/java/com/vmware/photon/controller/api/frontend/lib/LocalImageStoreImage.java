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

import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.transfer.streamVmdk.StreamVmdkReader;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class implementing ImageFolder. Assembly visibility.
 */
class LocalImageStoreImage implements Image {
  private static final Logger logger = LoggerFactory.getLogger(LocalImageStoreImage.class);
  private final String uploadFolder;
  private final String imageId;

  public LocalImageStoreImage(String uploadFolder, String imageId) {
    this.uploadFolder = uploadFolder;
    this.imageId = imageId;
  }

  @Override
  public String getImageId() {
    return imageId;
  }

  @Override
  public String getUploadFolder() {
    return uploadFolder;
  }

  @Override
  public long addFile(String fileName, InputStream inputStream, long fileSize)
      throws IOException, NameTakenException, InternalException {

    // Create file
    File target = toFile(fileName);
    logger.debug("create file %s/%s", target.getName());

    if (target.exists()) {
      throw new NameTakenException("image", imageId);
    }

    // Write to file.
    try (OutputStream outputStream = new FileOutputStream(target)) {
      return IOUtils.copyLarge(inputStream, outputStream);
    } catch (IOException e) {
      throw new InternalException(e);
    }
  }

  @Override
  public long addDisk(String fileName, InputStream inputStream)
      throws IOException, VmdkFormatException, NameTakenException, InternalException {
    try {
      return addFile(fileName, inputStream, 0);
    } finally {
      // validate disk type is streamOptimized, otherwise VmdkFormatException is thrown
      new StreamVmdkReader(new FileInputStream(toFile(fileName)));
    }
  }

  @Override
  public void close() {
    // Nothing.
  }

  /**
   * Create the given file in the image store.
   *
   * @param fileName
   * @return
   */
  private File toFile(String fileName) {
    // Create the store file name
    StringBuilder imageFileName = new StringBuilder();
    imageFileName.append(imageId).append(fileName);
    // Create file.
    return new File(uploadFolder, imageFileName.toString());
  }
}
