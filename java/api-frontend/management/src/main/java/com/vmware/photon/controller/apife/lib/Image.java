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

package com.vmware.photon.controller.apife.lib;

import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for adding files and disks to an image folder.
 */
public interface Image extends AutoCloseable {

  /**
   * Get image id.
   */
  String getImageId();

  /**
   * Get upload folder.
   */
  String getUploadFolder();

  /**
   * Upload an image file.
   *
   * @param fileName    file in the image
   * @param inputStream
   * @return
   * @throws InternalException
   * @throws java.io.IOException
   */
  long addFile(String fileName, InputStream inputStream, long fileSize) throws IOException, NameTakenException,
      InternalException;

  /**
   * Upload an image disk.
   *
   * @param fileName    file in the image
   * @param inputStream an input stream of bytes of the source file to copy from
   * @return the number of bytes copied
   * @throws NameTakenException
   * @throws InternalException
   * @throws java.io.IOException
   */
  long addDisk(String fileName, InputStream inputStream) throws IOException, VmdkFormatException,
      NameTakenException, InternalException;

  void close();
}
