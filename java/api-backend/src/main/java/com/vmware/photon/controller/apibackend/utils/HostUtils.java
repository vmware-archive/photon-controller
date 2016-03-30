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

package com.vmware.photon.controller.apibackend.utils;

import com.vmware.photon.controller.apibackend.ApiBackendFactory;
import com.vmware.photon.controller.apibackend.ApiBackendFactoryProvider;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.xenon.common.Service;

/**
 * This class implements utility functions for the api backend xenon host.
 */
public class HostUtils {

  public static CloudStoreHelper getCloudStoreHelper(Service service) {
    return getApiBackendFactory(service).createCloudStoreHelper();
  }

  public static ApiBackendFactory getApiBackendFactory(Service service) {
    return ((ApiBackendFactoryProvider) service.getHost()).getApiBackendFactory();
  }
}
