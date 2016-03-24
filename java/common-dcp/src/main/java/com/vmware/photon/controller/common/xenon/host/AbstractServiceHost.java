package com.vmware.photon.controller.common.xenon.host;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

import java.nio.file.Paths;

/**
 * This class implements a base class for {@link ServiceHost} classes in Photon Controller.
 */
public abstract class AbstractServiceHost extends ServiceHost {

  public AbstractServiceHost(XenonConfig xenonConfig) throws Throwable {

    Arguments arguments = new Arguments();
    arguments.port = xenonConfig.getPort();
    arguments.bindAddress = xenonConfig.getBindAddress();
    arguments.sandbox = Paths.get(xenonConfig.getStoragePath());

    if (xenonConfig.getRegistrationAddress() != null) {
      arguments.publicUri = UriUtils.buildUri(xenonConfig.getRegistrationAddress(), xenonConfig.getPort(), null, null)
          .toString();
    }

    if (xenonConfig.getPeerNodes() != null) {
      arguments.peerNodes = xenonConfig.getPeerNodes();
    }

    this.initialize(arguments);
  }
}
