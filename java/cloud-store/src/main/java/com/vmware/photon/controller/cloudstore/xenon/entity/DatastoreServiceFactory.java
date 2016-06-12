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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * This class implements a DCP micro-service which provides a factory for
 * {@link DatastoreService} instances.
 */
public class DatastoreServiceFactory extends FactoryService {

  public static final String SELF_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/datastores";

  public DatastoreServiceFactory() {
    super(DatastoreService.State.class);
    super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    // Symmetric replication allows the flat scheduler to avoid expensive broadcast queries
    super.setPeerNodeSelectorPath(ServiceUriPaths.NODE_SELECTOR_FOR_SYMMETRIC_REPLICATION);
  }

  @Override
  public Service createServiceInstance() throws Throwable {
    return new DatastoreService();
  }

  /**
   * Returns the document link for a given datastore ID.
   *
   * @param datastoreId datastore ID to get the document link for.
   * @return the document link for the datastore.
   */
  public static String getDocumentLink(String datastoreId) {
    return String.format("%s/%s", DatastoreServiceFactory.SELF_LINK, datastoreId);
  }

  @Override
  public void handleStop(Operation stop) {
    ServiceUtils.logWarning(this, "Stopping factory service %s", getSelfLink());
    super.handleStop(stop);
  }

  @Override
  public void handleDelete(Operation delete) {
    ServiceUtils.logWarning(this, "Deleting factory service %s", getSelfLink());
    super.handleDelete(delete);
  }
}
