/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.deployer.dcp.stateless;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * This Xenon service allows for downloading Vib files from the Vib directory.
 */

public class FileService extends StatelessService {

    public static final String SELF_LINK = ServiceUriPaths.DEPLOYER_ROOT + "/files";

    public FileService() {
      super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
      System.err.println(get.getUri().toString());
      DeployerContext deployerContext = HostUtils.getDeployerContext(this);

      String filepath = deployerContext.getVibDirectory() + get.getUri().getPath().replaceFirst(SELF_LINK, "");

      get.setContentType(Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM);
      try {
        get.setBody(Files.toString(new File(filepath), Charset.defaultCharset()));

        get.complete();
      } catch (IOException e) {
        get.fail(e);
      }
    }
}
