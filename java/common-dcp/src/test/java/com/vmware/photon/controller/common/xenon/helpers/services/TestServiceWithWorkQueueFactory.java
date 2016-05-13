package com.vmware.photon.controller.common.xenon.helpers.services;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * This class implements a factory for {@link TestServiceWithWorkQueue} instances.
 */
public class TestServiceWithWorkQueueFactory extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.SERVICES_ROOT + "/test-services-with-work-queue";

  public TestServiceWithWorkQueueFactory() {
    super(TestServiceWithWorkQueue.State.class);
  }

  @Override
  public Service createServiceInstance() {
    return new TestServiceWithWorkQueue();
  }
}
