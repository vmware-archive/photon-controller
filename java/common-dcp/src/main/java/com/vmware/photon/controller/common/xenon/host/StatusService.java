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

package com.vmware.photon.controller.common.xenon.host;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Class implementing service to get status of cloud store.
 */
public class StatusService extends StatelessService {

  public static final String SELF_LINK = ServiceUriPaths.STATUS_SERVICE;

  @Override
  public void handleGet(Operation get) {
    Status status = new Status(StatusType.INITIALIZING);
    BuildInfo buildInfo = ((XenonHostInfoProvider) getHost()).getBuildInfo();
    status.setBuild_info(buildInfo.toString());

    if (((PhotonControllerXenonHost) getHost()).isReady()) {
      status.setType(StatusType.READY);
    }
    get.setBody(status).complete();
  }
}
