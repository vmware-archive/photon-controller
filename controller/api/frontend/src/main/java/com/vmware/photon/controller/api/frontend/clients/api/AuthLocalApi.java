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

import com.vmware.photon.controller.api.client.resource.AuthApi;
import com.vmware.photon.controller.api.model.Auth;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * This class implements Auth API for communicating with APIFE locally.
 */
public class AuthLocalApi implements AuthApi {
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Auth getAuthStatus() throws IOException {
    return null;
  }

  @Override
  public void getAuthStatusAsync(FutureCallback<Auth> responseCallback) throws IOException {

  }
}
