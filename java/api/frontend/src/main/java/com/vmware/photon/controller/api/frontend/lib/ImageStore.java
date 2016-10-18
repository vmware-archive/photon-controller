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

import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;

/**
 * This interface manages image on datastore.
 */
public interface ImageStore {

  /**
   * Set hostIp to use for subsequent calls.
   *I
   * @param hostIp
   */
  void setHostIp(String hostIp);

  /**
   * Create an image folder.
   *
   * @param imageId
   * @return
   */
  Image createImage(String imageId) throws InternalException, ExternalException;

  /**
   * Make image usable to the system.
   *
   * @param image
   */
  void finalizeImage(Image image) throws InternalException, ExternalException;

  /**
   * Create image by cloning vm.
   *
   * @param image
   * @param vmId
   * @throws InternalException
   */
  void createImageFromVm(Image image, String vmId) throws ExternalException, InternalException;

  /**
   * Delete the entire upload folder on the host.
   */
  void deleteUploadFolder(Image image) throws InternalException, ExternalException;

  /**
   * @return true if replication of image file needs to be performed, otherwise false
   */
  boolean isReplicationNeeded();

  /**
   * @return the name of datastore
   */
  String getDatastore() throws ExternalException;
}
