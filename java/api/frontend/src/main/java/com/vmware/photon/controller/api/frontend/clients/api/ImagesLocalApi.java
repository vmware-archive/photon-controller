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
package com.vmware.photon.controller.api.frontend.clients.api;

import com.vmware.photon.controller.api.client.resource.ImagesApi;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.entity.mime.content.FileBody;

import java.io.IOException;

/**
 * This class implements Images API for communicating with APIFE locally.
 */
public class ImagesLocalApi implements ImagesApi {
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Task uploadImage(String inputFileName) throws IOException {
    return null;
  }

  @Override
  public Task uploadImage(String inputFileName, String replicationType) throws IOException {
    return null;
  }

  @Override
  public Task uploadImage(FileBody fileBody, String replicationType) throws IOException {
    return null;
  }

  @Override
  public Image getImage(String imageId) throws IOException {
    return null;
  }

  @Override
  public void getImageAsync(String imageId, FutureCallback<Image> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Image> getImages() throws IOException {
    return null;
  }

  @Override
  public void getImagesAsync(FutureCallback<ResourceList<Image>> responseCallback) throws IOException {

  }

  @Override
  public Task delete(String id) throws IOException {
    return null;
  }

  @Override
  public void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException {

  }
}
