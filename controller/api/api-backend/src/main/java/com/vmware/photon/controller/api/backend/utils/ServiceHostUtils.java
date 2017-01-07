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

package com.vmware.photon.controller.api.backend.utils;

import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.NsxClientFactoryProvider;
import com.vmware.xenon.common.ServiceHost;

/**
 * This class provides utility functions for Xenon service host.
 */
public class ServiceHostUtils {

  public static NsxClient getNsxClient(ServiceHost host,
                                       String endpoint,
                                       String username,
                                       String password) {
    return ((NsxClientFactoryProvider) host).getNsxClientFactory().create(
        endpoint, username, password);
  }

  public static CloudStoreHelper getCloudStoreHelper(ServiceHost host) {
    return ((CloudStoreHelperProvider) host).getCloudStoreHelper();
  }
}
