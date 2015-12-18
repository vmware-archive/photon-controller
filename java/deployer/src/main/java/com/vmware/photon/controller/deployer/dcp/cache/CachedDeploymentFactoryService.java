package com.vmware.photon.controller.deployer.dcp.cache;

import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * This class implements a DCP service which provides a factory for cached {@link DeploymentService} instances.
 */
public class CachedDeploymentFactoryService extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.SERVICES_ROOT + "/cached-items/deployments";

  public CachedDeploymentFactoryService() {
    super(DeploymentService.State.class);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new DeploymentService();
  }
}
