package com.vmware.photon.controller.deployer.dcp.cache;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * This class implements a PODO which represents a cached version of a (possibly remote) document.
 */
public class CachedDocument<T extends ServiceDocument> extends StatefulService {

  public CachedDocument(Class<T> clazz) {
    super(clazz);
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    return new ServiceDocument();
  }
}
